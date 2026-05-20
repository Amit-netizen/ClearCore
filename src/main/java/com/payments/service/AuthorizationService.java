package com.payments.service;

import com.payments.domain.entity.*;
import com.payments.domain.enums.*;
import com.payments.kafka.TransactionEventPublisher;
import com.payments.repository.*;
import com.payments.service.fraud.FraudRuleEngine;
import com.payments.service.fraud.FraudRuleEngine.FraudResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationService.class);

    private final CardRepository cardRepository;
    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final FraudRuleEngine fraudRuleEngine;
    private final TransactionEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;

    public AuthorizationService(CardRepository cardRepository,
                                MerchantRepository merchantRepository,
                                TransactionRepository transactionRepository,
                                LedgerService ledgerService,
                                FraudRuleEngine fraudRuleEngine,
                                TransactionEventPublisher eventPublisher,
                                PasswordEncoder passwordEncoder) {
        this.cardRepository = cardRepository;
        this.merchantRepository = merchantRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerService = ledgerService;
        this.fraudRuleEngine = fraudRuleEngine;
        this.eventPublisher = eventPublisher;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Transaction authorize(UUID cardId, UUID merchantId, long amount,
                                 String currency, String idempotencyKey, String ipAddress) {

        log.debug("Authorizing: card={}, merchant={}, amount={}", cardId, merchantId, amount);

        // Idempotency check
        if (idempotencyKey != null) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent request, returning existing transaction: {}", existing.get().getId());
                return existing.get();
            }
        }

        Card card = cardRepository.findByIdWithOptimisticLock(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + cardId));
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found: " + merchantId));

        Transaction txn = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .card(card)
                .merchant(merchant)
                .amount(amount)
                .currency(currency)
                .capturedAmount(0L)
                .reversedAmount(0L)
                .fraudScore(0)
                .ipAddress(ipAddress)
                .build();

        // Validate card
        ResponseCode validationResult = validateCard(card);
        if (validationResult != ResponseCode.APPROVED) {
            return decline(txn, validationResult);
        }

        // Fraud check
        FraudResult fraudResult = fraudRuleEngine.evaluate(card, amount, ipAddress);
        txn.setFraudScore(fraudResult.score());
        txn.setFraudFlags(fraudResult.flagsJson());
        if (fraudResult.shouldDecline()) {
            return decline(txn, fraudResult.responseCode());
        }

        // Balance check
        if (!card.hasSufficientBalance(amount)) {
            return decline(txn, ResponseCode.INSUFFICIENT_FUNDS);
        }

        // Deduct balance — triggers optimistic lock check
        card.deductBalance(amount);
        cardRepository.save(card);

        txn.setStatus(TransactionStatus.AUTHORIZED);
        txn.setResponseCode(ResponseCode.APPROVED);
        txn.setResponseMessage("Approved");
        txn.setAuthorizedAmount(amount);
        Transaction saved = transactionRepository.save(txn);

        ledgerService.recordAuthorization(saved);
        eventPublisher.publishTransactionEvent(saved, "AUTHORIZED");

        log.info("Transaction authorized: id={}, amount={}", saved.getId(), amount);
        return saved;
    }

    private ResponseCode validateCard(Card card) {
        if (!card.getActive()) return ResponseCode.RESTRICTED_CARD;
        if (card.isExpired()) return ResponseCode.EXPIRED_CARD;
        return ResponseCode.APPROVED;
    }

    private Transaction decline(Transaction txn, ResponseCode code) {
        txn.setStatus(TransactionStatus.DECLINED);
        txn.setResponseCode(code);
        txn.setResponseMessage(code.getMessage());
        txn.setAuthorizedAmount(0L);
        Transaction saved = transactionRepository.save(txn);
        eventPublisher.publishTransactionEvent(saved, "DECLINED");
        log.info("Transaction declined: code={}, message={}", code.getCode(), code.getMessage());
        return saved;
    }

    public boolean validateCvv(Card card, String rawCvv) {
        if (rawCvv == null || rawCvv.isBlank()) return false;
        return passwordEncoder.matches(rawCvv, card.getCvvHash());
    }
}
