package com.payments.service;

import com.payments.domain.entity.Card;
import com.payments.domain.entity.Transaction;
import com.payments.domain.enums.ResponseCode;
import com.payments.domain.enums.TransactionStatus;
import com.payments.kafka.TransactionEventPublisher;
import com.payments.repository.CardRepository;
import com.payments.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReversalService {

    private static final Logger log = LoggerFactory.getLogger(ReversalService.class);

    private final TransactionRepository transactionRepository;
    private final CardRepository cardRepository;
    private final LedgerService ledgerService;
    private final TransactionEventPublisher eventPublisher;

    public ReversalService(TransactionRepository transactionRepository,
                           CardRepository cardRepository,
                           LedgerService ledgerService,
                           TransactionEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.cardRepository = cardRepository;
        this.ledgerService = ledgerService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Transaction reverse(UUID transactionId, Long reversalAmount) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        if (!txn.canReverse())
            throw new IllegalStateException("Transaction cannot be reversed in status: " + txn.getStatus());

        Card card = txn.getCard();
        boolean isPreCapture = txn.getStatus() == TransactionStatus.AUTHORIZED
                || txn.getStatus() == TransactionStatus.PARTIALLY_CAPTURED;

        long amount = reversalAmount != null ? reversalAmount
                : (isPreCapture ? txn.getRemainingAuthorizedAmount() : txn.getCapturedAmount());

        if (amount <= 0) throw new IllegalArgumentException("Reversal amount must be positive");

        if (isPreCapture) {
            long maxReversible = txn.getRemainingAuthorizedAmount();
            if (amount > maxReversible)
                throw new IllegalArgumentException("Void amount " + amount + " exceeds held amount " + maxReversible);
            card.restoreBalance(amount);
            cardRepository.save(card);
            txn.setReversedAmount(txn.getReversedAmount() + amount);
            txn.setStatus(TransactionStatus.REVERSED);
            txn.setResponseCode(ResponseCode.APPROVED);
            txn.setResponseMessage("Voided");
            ledgerService.recordVoid(txn, amount);
            eventPublisher.publishTransactionEvent(txn, "REVERSED");
            log.info("Transaction voided: id={}, amount={}", transactionId, amount);
        } else {
            long maxRefundable = txn.getCapturedAmount() - txn.getReversedAmount();
            if (amount > maxRefundable)
                throw new IllegalArgumentException("Refund amount " + amount + " exceeds captured amount " + maxRefundable);
            card.restoreBalance(amount);
            cardRepository.save(card);
            txn.setReversedAmount(txn.getReversedAmount() + amount);
            if (txn.getReversedAmount().equals(txn.getCapturedAmount())) {
                txn.setStatus(TransactionStatus.REFUNDED);
                txn.setResponseMessage("Fully refunded");
            } else {
                txn.setResponseMessage("Partially refunded: " + txn.getReversedAmount());
            }
            txn.setResponseCode(ResponseCode.APPROVED);
            ledgerService.recordRefund(txn, amount);
            eventPublisher.publishTransactionEvent(txn, "REFUNDED");
            log.info("Transaction refunded: id={}, amount={}", transactionId, amount);
        }

        return transactionRepository.save(txn);
    }
}
