package com.payments.service;

import com.payments.domain.entity.Transaction;
import com.payments.domain.enums.ResponseCode;
import com.payments.domain.enums.TransactionStatus;
import com.payments.kafka.TransactionEventPublisher;
import com.payments.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CaptureService {

    private static final Logger log = LoggerFactory.getLogger(CaptureService.class);

    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final TransactionEventPublisher eventPublisher;

    public CaptureService(TransactionRepository transactionRepository,
                          LedgerService ledgerService,
                          TransactionEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.ledgerService = ledgerService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Transaction capture(UUID transactionId, long captureAmount) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        if (!txn.canCapture())
            throw new IllegalStateException("Transaction cannot be captured in status: " + txn.getStatus());

        long remaining = txn.getRemainingAuthorizedAmount();
        if (captureAmount > remaining)
            throw new IllegalArgumentException("Capture amount " + captureAmount + " exceeds remaining authorized amount " + remaining);
        if (captureAmount <= 0)
            throw new IllegalArgumentException("Capture amount must be positive");

        txn.setCapturedAmount(txn.getCapturedAmount() + captureAmount);

        if (txn.getRemainingAuthorizedAmount() == 0) {
            txn.setStatus(TransactionStatus.CAPTURED);
            txn.setResponseCode(ResponseCode.APPROVED);
            txn.setResponseMessage("Fully captured");
        } else {
            txn.setStatus(TransactionStatus.PARTIALLY_CAPTURED);
            txn.setResponseCode(ResponseCode.PARTIAL_APPROVAL);
            txn.setResponseMessage("Partial capture: " + captureAmount);
        }

        Transaction saved = transactionRepository.save(txn);
        ledgerService.recordCapture(saved, captureAmount);
        eventPublisher.publishTransactionEvent(saved, "CAPTURED");
        log.info("Transaction captured: id={}, amount={}", saved.getId(), captureAmount);
        return saved;
    }
}
