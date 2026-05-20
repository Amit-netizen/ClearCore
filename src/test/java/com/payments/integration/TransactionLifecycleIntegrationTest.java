package com.payments.integration;

import com.payments.domain.entity.Card;
import com.payments.domain.entity.Merchant;
import com.payments.domain.entity.Transaction;
import com.payments.domain.enums.ResponseCode;
import com.payments.domain.enums.TransactionStatus;
import com.payments.repository.CardRepository;
import com.payments.repository.MerchantRepository;
import com.payments.repository.TransactionRepository;
import com.payments.service.AuthorizationService;
import com.payments.service.CaptureService;
import com.payments.service.LedgerService;
import com.payments.service.ReversalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class TransactionLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired AuthorizationService authorizationService;
    @Autowired CaptureService captureService;
    @Autowired ReversalService reversalService;
    @Autowired LedgerService ledgerService;
    @Autowired CardRepository cardRepository;
    @Autowired MerchantRepository merchantRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Card card;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = merchantRepository.save(Merchant.builder()
                .name("Integration Merchant")
                .email("int_" + System.nanoTime() + "@test.com")
                .balance(0L)
                .active(true)
                .version(0L)
                .build());

        card = cardRepository.save(Card.builder()
                .cardNumber("4111-TEST-" + System.nanoTime())
                .cardHolderName("Test User")
                .expiryMonth(12)
                .expiryYear(LocalDateTime.now().getYear() + 3)
                .cvvHash(passwordEncoder.encode("123"))
                .availableBalance(1_000_000L)   // ₹10,000
                .creditLimit(2_000_000L)
                .active(true)
                .version(0L)
                .build());
    }

    @Test
    @DisplayName("Full lifecycle: authorize → capture → refund")
    void fullLifecycle_authorizeCaptureThenRefund() {
        long initialBalance = card.getAvailableBalance();

        // Step 1: Authorize
        Transaction txn = authorizationService.authorize(
                card.getId(), merchant.getId(), 500000L, "INR", null, "127.0.0.1");

        assertThat(txn.getStatus()).isEqualTo(TransactionStatus.AUTHORIZED);
        assertThat(txn.getResponseCode()).isEqualTo(ResponseCode.APPROVED);

        // Card balance should be reduced
        Card afterAuth = cardRepository.findById(card.getId()).orElseThrow();
        assertThat(afterAuth.getAvailableBalance()).isEqualTo(initialBalance - 500000L);

        // Step 2: Capture
        Transaction captured = captureService.capture(txn.getId(), 500000L);
        assertThat(captured.getStatus()).isEqualTo(TransactionStatus.CAPTURED);
        assertThat(captured.getCapturedAmount()).isEqualTo(500000L);

        // Step 3: Refund
        Transaction refunded = reversalService.reverse(txn.getId(), null);
        assertThat(refunded.getStatus()).isEqualTo(TransactionStatus.REFUNDED);

        // Balance should be restored
        Card afterRefund = cardRepository.findById(card.getId()).orElseThrow();
        assertThat(afterRefund.getAvailableBalance()).isEqualTo(initialBalance);

        // Ledger must be balanced
        long totalDebits = ledgerService.getTotalDebits();
        long totalCredits = ledgerService.getTotalCredits();
        assertThat(totalDebits).isEqualTo(totalCredits);
    }

    @Test
    @DisplayName("Partial capture then partial void lifecycle")
    void partialCaptureAndVoid_lifecycle() {
        // Authorize ₹5000
        Transaction txn = authorizationService.authorize(
                card.getId(), merchant.getId(), 500000L, "INR", null, "127.0.0.1");

        // Capture ₹3000
        captureService.capture(txn.getId(), 300000L);

        // Void remaining ₹2000 hold
        Transaction voided = reversalService.reverse(txn.getId(), 200000L);

        assertThat(voided.getReversedAmount()).isEqualTo(200000L);
    }

    @Test
    @DisplayName("Idempotency: same key returns same transaction")
    void idempotency_sameKey_sameResult() {
        String idempotencyKey = "test-key-" + System.nanoTime();

        Transaction first = authorizationService.authorize(
                card.getId(), merchant.getId(), 100000L, "INR", idempotencyKey, "127.0.0.1");
        Transaction second = authorizationService.authorize(
                card.getId(), merchant.getId(), 100000L, "INR", idempotencyKey, "127.0.0.1");

        assertThat(first.getId()).isEqualTo(second.getId());

        // Card balance should only be deducted once
        Card afterDouble = cardRepository.findById(card.getId()).orElseThrow();
        assertThat(afterDouble.getAvailableBalance())
                .isEqualTo(card.getAvailableBalance() - 100000L);
    }

    @Test
    @DisplayName("Declined transaction does not affect card balance")
    void declined_doesNotAffectBalance() {
        long initialBalance = cardRepository.findById(card.getId()).orElseThrow().getAvailableBalance();

        // Try to charge more than the balance
        Transaction declined = authorizationService.authorize(
                card.getId(), merchant.getId(), 999_999_999L, "INR", null, "127.0.0.1");

        assertThat(declined.getStatus()).isEqualTo(TransactionStatus.DECLINED);
        assertThat(declined.getResponseCode()).isEqualTo(ResponseCode.INSUFFICIENT_FUNDS);

        Card afterDecline = cardRepository.findById(card.getId()).orElseThrow();
        assertThat(afterDecline.getAvailableBalance()).isEqualTo(initialBalance);
    }
}
