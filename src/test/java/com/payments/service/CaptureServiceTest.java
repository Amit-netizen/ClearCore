package com.payments.service;

import com.payments.domain.entity.Card;
import com.payments.domain.entity.Merchant;
import com.payments.domain.entity.Transaction;
import com.payments.domain.enums.ResponseCode;
import com.payments.domain.enums.TransactionStatus;
import com.payments.kafka.TransactionEventPublisher;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaptureServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock LedgerService ledgerService;
    @Mock TransactionEventPublisher eventPublisher;

    @InjectMocks CaptureService captureService;

    private Transaction authorizedTxn;
    private UUID txnId;

    @BeforeEach
    void setUp() {
        txnId = UUID.randomUUID();
        Card card = Card.builder().id(UUID.randomUUID()).build();
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
                .currency("INR")
                .version(0L)
                .build();

        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Full capture moves status to CAPTURED")
    void capture_fullAmount_statusCaptured() {
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(authorizedTxn));

        Transaction result = captureService.capture(txnId, 100000L);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.CAPTURED);
        assertThat(result.getCapturedAmount()).isEqualTo(100000L);
        verify(ledgerService).recordCapture(any(), eq(100000L));
        verify(eventPublisher).publishTransactionEvent(any(), eq("CAPTURED"));
    }

    @Test
    @DisplayName("Partial capture moves status to PARTIALLY_CAPTURED")
    void capture_partialAmount_statusPartiallyCaptured() {
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(authorizedTxn));

        Transaction result = captureService.capture(txnId, 60000L);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PARTIALLY_CAPTURED);
        assertThat(result.getCapturedAmount()).isEqualTo(60000L);
        assertThat(result.getRemainingAuthorizedAmount()).isEqualTo(40000L);
    }

    @Test
    @DisplayName("Second partial capture accumulates correctly")
    void capture_twoPartialCaptures_accumulatesCorrectly() {
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(authorizedTxn));
        captureService.capture(txnId, 60000L);

        // Simulate first capture saved
        authorizedTxn.setCapturedAmount(60000L);
        authorizedTxn.setStatus(TransactionStatus.PARTIALLY_CAPTURED);

        Transaction result = captureService.capture(txnId, 40000L);
        assertThat(result.getCapturedAmount()).isEqualTo(100000L);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.CAPTURED);
    }

    @Test
    @DisplayName("Capture amount exceeding authorized amount throws exception")
    void capture_exceedsAuthorized_throwsException() {
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(authorizedTxn));

        assertThatThrownBy(() -> captureService.capture(txnId, 200000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds remaining authorized amount");
    }

    @Test
    @DisplayName("Capture on DECLINED transaction throws exception")
    void capture_declinedTransaction_throwsException() {
        authorizedTxn.setStatus(TransactionStatus.DECLINED);
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(authorizedTxn));

        assertThatThrownBy(() -> captureService.capture(txnId, 100000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be captured");
    }

    @Test
    @DisplayName("Zero capture amount throws exception")
    void capture_zeroAmount_throwsException() {
        when(transactionRepository.findById(txnId)).thenReturn(Optional.of(authorizedTxn));

        assertThatThrownBy(() -> captureService.capture(txnId, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    @DisplayName("Capture on non-existent transaction throws exception")
    void capture_notFound_throwsException() {
        when(transactionRepository.findById(txnId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> captureService.capture(txnId, 100000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction not found");
    }
}
