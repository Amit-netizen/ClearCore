package com.payments.service;

import com.payments.domain.entity.Card;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class CardTest {

    @Test
    @DisplayName("Card expiry check — expires if year is in the past")
    void isExpired_pastYear_returnsTrue() {
        Card card = Card.builder()
                .expiryMonth(1)
                .expiryYear(2020)
                .build();
        assertThat(card.isExpired()).isTrue();
    }

    @Test
    @DisplayName("Card expiry check — not expired if year is in the future")
    void isExpired_futureYear_returnsFalse() {
        Card card = Card.builder()
                .expiryMonth(1)
                .expiryYear(LocalDateTime.now().getYear() + 2)
                .build();
        assertThat(card.isExpired()).isFalse();
    }

    @Test
    @DisplayName("Card expiry — expired if same year but past month")
    void isExpired_currentYearPastMonth_returnsTrue() {
        int currentYear = LocalDateTime.now().getYear();
        int pastMonth = LocalDateTime.now().getMonthValue() - 1;
        if (pastMonth < 1) {
            // January edge case — skip
            return;
        }
        Card card = Card.builder()
                .expiryMonth(pastMonth)
                .expiryYear(currentYear)
                .build();
        assertThat(card.isExpired()).isTrue();
    }

    @Test
    @DisplayName("hasSufficientBalance returns true when balance >= amount")
    void hasSufficientBalance_sufficient_returnsTrue() {
        Card card = Card.builder().availableBalance(500000L).build();
        assertThat(card.hasSufficientBalance(500000L)).isTrue();
        assertThat(card.hasSufficientBalance(499999L)).isTrue();
    }

    @Test
    @DisplayName("hasSufficientBalance returns false when balance < amount")
    void hasSufficientBalance_insufficient_returnsFalse() {
        Card card = Card.builder().availableBalance(100L).build();
        assertThat(card.hasSufficientBalance(101L)).isFalse();
    }

    @Test
    @DisplayName("deductBalance reduces balance correctly")
    void deductBalance_valid_reducesBalance() {
        Card card = Card.builder().availableBalance(500000L).build();
        card.deductBalance(200000L);
        assertThat(card.getAvailableBalance()).isEqualTo(300000L);
    }

    @Test
    @DisplayName("deductBalance throws when insufficient")
    void deductBalance_insufficient_throws() {
        Card card = Card.builder().availableBalance(100L).build();
        assertThatThrownBy(() -> card.deductBalance(200L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    @DisplayName("restoreBalance adds amount correctly")
    void restoreBalance_addsAmount() {
        Card card = Card.builder().availableBalance(100L).build();
        card.restoreBalance(200L);
        assertThat(card.getAvailableBalance()).isEqualTo(300L);
    }
}
