package com.payments.fraud;

import com.payments.domain.entity.Card;
import com.payments.domain.enums.ResponseCode;
import com.payments.repository.TransactionRepository;
import com.payments.service.fraud.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudRuleTest {

    @Mock TransactionRepository transactionRepository;

    @InjectMocks VelocityCheckRule velocityCheckRule;

    private AmountThresholdRule amountThresholdRule;
    private GeoMismatchRule geoMismatchRule;

    private Card card;

    @BeforeEach
    void setUp() {
        card = Card.builder()
                .id(UUID.randomUUID())
                .active(true)
                .expiryMonth(12)
                .expiryYear(LocalDateTime.now().getYear() + 2)
                .build();

        amountThresholdRule = new AmountThresholdRule();
        geoMismatchRule = new GeoMismatchRule();

        // Set thresholds via reflection (simulates @Value injection in tests)
        ReflectionTestUtils.setField(velocityCheckRule, "velocityLimitPerMinute", 5);
        ReflectionTestUtils.setField(amountThresholdRule, "thresholdInPaise", 10_000_000L);
    }

    // ─────────────────────────────────────────────
    // VELOCITY CHECK
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Velocity check passes when under limit")
    void velocityCheck_underLimit_passes() {
        when(transactionRepository.countByCardIdAndCreatedAtAfter(eq(card.getId()), any()))
                .thenReturn(3L);

        FraudRule.RuleResult result = velocityCheckRule.evaluate(card, 10000L, "127.0.0.1");

        assertThat(result.triggered()).isFalse();
        assertThat(result.hardDecline()).isFalse();
    }

    @Test
    @DisplayName("Velocity check declines when at limit (exactly 5)")
    void velocityCheck_atLimit_declines() {
        when(transactionRepository.countByCardIdAndCreatedAtAfter(eq(card.getId()), any()))
                .thenReturn(5L);

        FraudRule.RuleResult result = velocityCheckRule.evaluate(card, 10000L, "127.0.0.1");

        assertThat(result.triggered()).isTrue();
        assertThat(result.hardDecline()).isTrue();
        assertThat(result.responseCode()).isEqualTo(ResponseCode.RESTRICTED_CARD);
        assertThat(result.responseCode().getCode()).isEqualTo("62");
    }

    @Test
    @DisplayName("Velocity check declines when over limit")
    void velocityCheck_overLimit_declines() {
        when(transactionRepository.countByCardIdAndCreatedAtAfter(eq(card.getId()), any()))
                .thenReturn(10L);

        FraudRule.RuleResult result = velocityCheckRule.evaluate(card, 10000L, "127.0.0.1");

        assertThat(result.triggered()).isTrue();
        assertThat(result.hardDecline()).isTrue();
    }

    // ─────────────────────────────────────────────
    // AMOUNT THRESHOLD
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Amount threshold passes for normal transaction")
    void amountThreshold_normalAmount_passes() {
        FraudRule.RuleResult result = amountThresholdRule.evaluate(card, 50000L, "127.0.0.1");

        assertThat(result.triggered()).isFalse();
    }

    @Test
    @DisplayName("Amount threshold flags for amount over ₹1L")
    void amountThreshold_overThreshold_flags() {
        FraudRule.RuleResult result = amountThresholdRule.evaluate(card, 15_000_000L, "127.0.0.1");

        assertThat(result.triggered()).isTrue();
        assertThat(result.hardDecline()).isFalse();  // soft flag, not hard decline
        assertThat(result.scoreContribution()).isGreaterThan(0);
        assertThat(result.ruleCode()).isEqualTo("AMOUNT_THRESHOLD");
    }

    @Test
    @DisplayName("Amount threshold does not flag amount exactly at threshold")
    void amountThreshold_exactlyAtThreshold_passes() {
        FraudRule.RuleResult result = amountThresholdRule.evaluate(card, 10_000_000L, "127.0.0.1");
        assertThat(result.triggered()).isFalse();
    }

    // ─────────────────────────────────────────────
    // GEO MISMATCH (stub)
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Geo mismatch passes for private IP (local dev)")
    void geoMismatch_privateIp_passes() {
        assertThat(geoMismatchRule.evaluate(card, 10000L, "127.0.0.1").triggered()).isFalse();
        assertThat(geoMismatchRule.evaluate(card, 10000L, "192.168.1.1").triggered()).isFalse();
        assertThat(geoMismatchRule.evaluate(card, 10000L, "10.0.0.1").triggered()).isFalse();
    }

    @Test
    @DisplayName("Geo mismatch passes for null IP")
    void geoMismatch_nullIp_passes() {
        assertThat(geoMismatchRule.evaluate(card, 10000L, null).triggered()).isFalse();
    }

    // ─────────────────────────────────────────────
    // FRAUD RULE RESULT FACTORY METHODS
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("RuleResult.pass creates non-triggered result")
    void ruleResult_pass_isCorrect() {
        FraudRule.RuleResult result = FraudRule.RuleResult.pass("TEST_RULE");
        assertThat(result.triggered()).isFalse();
        assertThat(result.scoreContribution()).isZero();
        assertThat(result.hardDecline()).isFalse();
    }

    @Test
    @DisplayName("RuleResult.decline creates hard-decline result")
    void ruleResult_decline_isCorrect() {
        FraudRule.RuleResult result = FraudRule.RuleResult.decline(
                "TEST_RULE", "reason", ResponseCode.RESTRICTED_CARD);
        assertThat(result.triggered()).isTrue();
        assertThat(result.hardDecline()).isTrue();
        assertThat(result.scoreContribution()).isEqualTo(100);
    }
}
