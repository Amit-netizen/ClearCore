package com.payments.service;

import com.payments.domain.entity.Card;
import com.payments.domain.entity.Merchant;
import com.payments.domain.entity.Transaction;
import com.payments.domain.enums.ResponseCode;
import com.payments.domain.enums.TransactionStatus;
import com.payments.kafka.TransactionEventPublisher;
import com.payments.repository.CardRepository;
import com.payments.repository.MerchantRepository;
import com.payments.repository.TransactionRepository;
import com.payments.service.fraud.FraudRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock CardRepository cardRepository;
    @Mock MerchantRepository merchantRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock LedgerService ledgerService;
    @Mock FraudRuleEngine fraudRuleEngine;
    @Mock TransactionEventPublisher eventPublisher;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks AuthorizationService authorizationService;

    private Card validCard;
    private Merchant merchant;
    private UUID cardId;
    private UUID merchantId;

    @BeforeEach
    void setUp() {
        cardId = UUID.randomUUID();
        merchantId = UUID.randomUUID();

        validCard = Card.builder()
                .id(cardId)
                .cardNumber("4111-1111-1111-1111")
                .cardHolderName("Amit Kumar")
                .expiryMonth(12)
                .expiryYear(LocalDateTime.now().getYear() + 2)
                .cvvHash("$2a$10$hashed")
                .availableBalance(500000L)
                .creditLimit(1000000L)
                .active(true)
                .version(0L)
                .build();

        merchant = Merchant.builder()
                .id(merchantId)
                .name("Test Merchant")
                .email("test@merchant.com")
                .balance(0L)
                .active(true)
                .version(0L)
                .build();

        // Default: fraud engine passes
        when(fraudRuleEngine.evaluate(any(), anyLong(), any()))
                .thenReturn(new FraudRuleEngine.FraudResult(0, "[]", false, ResponseCode.APPROVED));

        // Default: save returns the passed entity
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─────────────────────────────────────────────
    // HAPPY PATH
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should approve transaction for valid card with sufficient balance")
    void authorize_validCard_sufficientBalance_shouldApprove() {
        when(cardRepository.findByIdWithOptimisticLock(cardId)).thenReturn(Optional.of(validCard));
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        Transaction result = authorizationService.authorize(
                cardId, merchantId, 100000L, "INR", null, "127.0.0.1");

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.AUTHORIZED);
        assertThat(result.getResponseCode()).isEqualTo(ResponseCode.APPROVED);
        assertThat(result.getAuthorizedAmount()).isEqualTo(100000L);
        verify(ledgerService).recordAuthorization(any());
        verify(eventPublisher).publishTransactionEvent(any(), eq("AUTHORIZED"));
    }

    // ─────────────────────────────────────────────
    // IDEMPOTENCY
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return existing transaction for duplicate idempotency key")
    void authorize_duplicateIdempotencyKey_shouldReturnExisting() {
        Transaction existing = Transaction.builder()
                .id(UUID.randomUUID())
                .status(TransactionStatus.AUTHORIZED)
                .responseCode(ResponseCode.APPROVED)
                .build();
        when(transactionRepository.findByIdempotencyKey("idem-key-123"))
                .thenReturn(Optional.of(existing));

        Transaction result = authorizationService.authorize(
                cardId, merchantId, 100000L, "INR", "idem-key-123", "127.0.0.1");

        assertThat(result.getId()).isEqualTo(existing.getId());
        // Should NOT call card repo — returned early
        verify(cardRepository, never()).findByIdWithOptimisticLock(any());
    }

    // ─────────────────────────────────────────────
    // CARD VALIDATION BRANCHES
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should decline when card is expired")
    void authorize_expiredCard_shouldDeclineWithCode54() {
        validCard.setExpiryYear(2020);
        validCard.setExpiryMonth(1);
        when(cardRepository.findByIdWithOptimisticLock(cardId)).thenReturn(Optional.of(validCard));
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        Transaction result = authorizationService.authorize(
                cardId, merchantId, 100000L, "INR", null, "127.0.0.1");

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.DECLINED);
        assertThat(result.getResponseCode()).isEqualTo(ResponseCode.EXPIRED_CARD);
        assertThat(result.getResponseCode().getCode()).isEqualTo("54");
        verify(eventPublisher).publishTransactionEvent(any(), eq("DECLINED"));
    }

    @Test
    @DisplayName("Should decline when card is inactive")
    void authorize_inactiveCard_shouldDeclineWithCode62() {
        validCard.setActive(false);
        when(cardRepository.findByIdWithOptimisticLock(cardId)).thenReturn(Optional.of(validCard));
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        Transaction result = authorizationService.authorize(
                cardId, merchantId, 100000L, "INR", null, "127.0.0.1");

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.DECLINED);
        assertThat(result.getResponseCode()).isEqualTo(ResponseCode.RESTRICTED_CARD);
    }

    @Test
    @DisplayName("Should decline when balance is insufficient — code 51")
    void authorize_insufficientFunds_shouldDeclineWithCode51() {
        validCard.setAvailableBalance(1000L);  // only ₹10
        when(cardRepository.findByIdWithOptimisticLock(cardId)).thenReturn(Optional.of(validCard));
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        Transaction result = authorizationService.authorize(
                cardId, merchantId, 100000L, "INR", null, "127.0.0.1");

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.DECLINED);
        assertThat(result.getResponseCode()).isEqualTo(ResponseCode.INSUFFICIENT_FUNDS);
        assertThat(result.getResponseCode().getCode()).isEqualTo("51");
    }

    // ─────────────────────────────────────────────
    // FRAUD DECLINE
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should decline when fraud engine triggers hard decline")
    void authorize_fraudDetected_shouldDecline() {
        when(cardRepository.findByIdWithOptimisticLock(cardId)).thenReturn(Optional.of(validCard));
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(fraudRuleEngine.evaluate(any(), anyLong(), any()))
                .thenReturn(new FraudRuleEngine.FraudResult(
                        100, "[\"VELOCITY_CHECK: exceeded\"]", true, ResponseCode.RESTRICTED_CARD));

        Transaction result = authorizationService.authorize(
                cardId, merchantId, 100000L, "INR", null, "1.2.3.4");

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.DECLINED);
        assertThat(result.getFraudScore()).isEqualTo(100);
    }

    // ─────────────────────────────────────────────
    // BALANCE DEDUCTION
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Should deduct card balance on approval")
    void authorize_approved_shouldDeductCardBalance() {
        long initialBalance = 500000L;
        long txnAmount = 100000L;
        validCard.setAvailableBalance(initialBalance);

        when(cardRepository.findByIdWithOptimisticLock(cardId)).thenReturn(Optional.of(validCard));
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(transactionRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        authorizationService.authorize(cardId, merchantId, txnAmount, "INR", null, "127.0.0.1");

        assertThat(validCard.getAvailableBalance()).isEqualTo(initialBalance - txnAmount);
        verify(cardRepository).save(validCard);
    }

    // ─────────────────────────────────────────────
    // CVV VALIDATION
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("validateCvv returns true when CVV matches hash")
    void validateCvv_matching_returnsTrue() {
        when(passwordEncoder.matches("123", "$2a$10$hashed")).thenReturn(true);
        assertThat(authorizationService.validateCvv(validCard, "123")).isTrue();
    }

    @Test
    @DisplayName("validateCvv returns false when CVV is null")
    void validateCvv_null_returnsFalse() {
        assertThat(authorizationService.validateCvv(validCard, null)).isFalse();
    }

    @Test
    @DisplayName("validateCvv returns false when CVV is blank")
    void validateCvv_blank_returnsFalse() {
        assertThat(authorizationService.validateCvv(validCard, "   ")).isFalse();
    }
}
