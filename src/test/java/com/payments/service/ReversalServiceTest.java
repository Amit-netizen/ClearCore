package com.payments.service;

import com.payments.domain.entity.Card;
import com.payments.domain.entity.Merchant;
import com.payments.domain.entity.Transaction;
import com.payments.domain.enums.ResponseCode;
import com.payments.domain.enums.TransactionStatus;
import com.payments.kafka.TransactionEventPublisher;
import com.payments.repository.CardRepository;
import com.payments.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReversalServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock CardRepository cardRepository;
    @Mock LedgerService ledgerService;
    @Mock TransactionEventPublisher eventPublisher;

    @InjectMocks ReversalService reversalService;

    private Card card;
    private Transaction authorizedTxn;
    private Transaction capturedTxn;
    private UUID txnId;

    @BeforeEach
    void setUp() {
        txnId = UUID.randomUUID();
        card = Card.builder()
                .id(UUID.randomUUID())
                .availableBalance(400000L)
                .version(0L)
                .build();
        Merchant merchant = Merchant.builder().id(UUID.randomUUID()).build();

        authorizedTxn = Transaction.builder()
                .id(txnId)
                .card(card)
                .merchant(merchant)
                .amount(100000L)
                .authorizedAmount(100000L)
                .capturedAmount(0L)
                .reversedAmount(0L)
                .status(TransactionStatus.AUTHORIZED)
                .responseCode(ResponseCode.APPROVED)
                .version(0L)
                .build();

        capturedTxn = Transaction.builder()
                .id(UUID.randomUUID())
                .card(card)
                .merchant(merchant)
                .amount(100000L)
                .authorizedAmount(100000L)
                .capturedAmount(100000L)
                .reversedAmount(0L)
                .status(TransactionStatus.CAPTURED)
                .responseCode(ResponseCode.APPROVED)
                .version(0L)
                .build();

        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─────────────────────────────────────────────
    // VOID (pre-capture)
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Void on AUTHORIZED transaction restores card balance")
    void reverse_authorized_voidRestoresBalance() {
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(authorizedTxn));

        long balanceBefore = card.getAvailableBalance();
        Transaction result = reversalService.reverse(txnId, null);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.REVERSED);
        assertThat(card.getAvailableBalance()).isEqualTo(balanceBefore + 100000L);
        verify(ledgerService).recordVoid(any(), eq(100000L));
        verify(eventPublisher).publishTransactionEvent(any(), eq("REVERSED"));
    }

    @Test
    @DisplayName("Partial void on AUTHORIZED transaction reduces hold")
    void reverse_authorized_partialVoid() {
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(authorizedTxn));

        long balanceBefore = card.getAvailableBalance();
        reversalService.reverse(txnId, 40000L);

        assertThat(card.getAvailableBalance()).isEqualTo(balanceBefore + 40000L);
    }

    // ─────────────────────────────────────────────
    // REFUND (post-capture)
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Full refund on CAPTURED transaction credits card back")
    void reverse_captured_fullRefund() {
        when(transactionRepository.findById(capturedTxn.getId()))
                .thenReturn(Optional.of(capturedTxn));

        long balanceBefore = card.getAvailableBalance();
        Transaction result = reversalService.reverse(capturedTxn.getId(), null);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.REFUNDED);
        assertThat(card.getAvailableBalance()).isEqualTo(balanceBefore + 100000L);
        verify(ledgerService).recordRefund(any(), eq(100000L));
        verify(eventPublisher).publishTransactionEvent(any(), eq("REFUNDED"));
    }

    @Test
    @DisplayName("Partial refund leaves transaction in CAPTURED state with partial reversed amount")
    void reverse_captured_partialRefund() {
        when(transactionRepository.findById(capturedTxn.getId()))
                .thenReturn(Optional.of(capturedTxn));

        Transaction result = reversalService.reverse(capturedTxn.getId(), 40000L);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.CAPTURED); // not fully refunded
        assertThat(result.getReversedAmount()).isEqualTo(40000L);
    }

    // ─────────────────────────────────────────────
    // ERROR CASES
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("Reversal on DECLINED transaction throws exception")
    void reverse_declined_throwsException() {
        authorizedTxn.setStatus(TransactionStatus.DECLINED);
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(authorizedTxn));

        assertThatThrownBy(() -> reversalService.reverse(txnId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be reversed");
    }

    @Test
    @DisplayName("Void amount exceeding authorized amount throws exception")
    void reverse_voidExceedsAuthorized_throwsException() {
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(authorizedTxn));

        assertThatThrownBy(() -> reversalService.reverse(txnId, 999999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Void amount");
    }

    @Test
    @DisplayName("Refund amount exceeding captured amount throws exception")
    void reverse_refundExceedsCaptured_throwsException() {
        when(transactionRepository.findById(capturedTxn.getId()))
                .thenReturn(Optional.of(capturedTxn));

        assertThatThrownBy(() -> reversalService.reverse(capturedTxn.getId(), 999999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refund amount");
    }
}
