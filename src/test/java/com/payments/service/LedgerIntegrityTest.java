package com.payments.service;

import com.payments.domain.entity.Card;
import com.payments.domain.entity.LedgerEntry;
import com.payments.domain.entity.Merchant;
import com.payments.domain.entity.Transaction;
import com.payments.domain.enums.LedgerEntryType;
import com.payments.domain.enums.ResponseCode;
import com.payments.domain.enums.TransactionStatus;
import com.payments.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies the fundamental ledger invariant:
 *   SUM(DEBIT entries) == SUM(CREDIT entries)
 * after any sequence of operations.
 */
@ExtendWith(MockitoExtension.class)
class LedgerIntegrityTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks LedgerService ledgerService;

    @Captor ArgumentCaptor<LedgerEntry> entryCaptor;

    private Transaction txn;
    private List<LedgerEntry> capturedEntries;

    @BeforeEach
    void setUp() {
        capturedEntries = new ArrayList<>();
        Card card = Card.builder().id(UUID.randomUUID()).build();
        Merchant merchant = Merchant.builder().id(UUID.randomUUID()).build();

        txn = Transaction.builder()
                .id(UUID.randomUUID())
                .card(card)
                .merchant(merchant)
                .amount(100000L)
                .authorizedAmount(100000L)
                .capturedAmount(100000L)
                .reversedAmount(0L)
                .status(TransactionStatus.AUTHORIZED)
                .responseCode(ResponseCode.APPROVED)
                .build();

        // Collect all saved entries
        when(ledgerEntryRepository.save(entryCaptor.capture()))
                .thenAnswer(inv -> {
                    capturedEntries.add(entryCaptor.getValue());
                    return entryCaptor.getValue();
                });
    }

    @Test
    @DisplayName("Authorization creates balanced debit/credit pair")
    void authorize_createsBalancedEntries() {
        ledgerService.recordAuthorization(txn);
        assertLedgerBalanced();
    }

    @Test
    @DisplayName("Capture creates balanced debit/credit pair")
    void capture_createsBalancedEntries() {
        ledgerService.recordCapture(txn, 100000L);
        assertLedgerBalanced();
    }

    @Test
    @DisplayName("Void creates balanced debit/credit pair")
    void void_createsBalancedEntries() {
        ledgerService.recordVoid(txn, 100000L);
        assertLedgerBalanced();
    }

    @Test
    @DisplayName("Refund creates balanced debit/credit pair")
    void refund_createsBalancedEntries() {
        ledgerService.recordRefund(txn, 100000L);
        assertLedgerBalanced();
    }

    @Test
    @DisplayName("Settlement creates balanced debit/credit pair")
    void settlement_createsBalancedEntries() {
        ledgerService.recordSettlement(txn);
        assertLedgerBalanced();
    }

    @Test
    @DisplayName("Full lifecycle: authorize → capture → refund remains balanced")
    void fullLifecycle_remainsBalanced() {
        ledgerService.recordAuthorization(txn);
        ledgerService.recordCapture(txn, 100000L);
        ledgerService.recordRefund(txn, 100000L);
        assertLedgerBalanced();
    }

    @Test
    @DisplayName("Partial capture + partial refund sequence remains balanced")
    void partialCaptureAndRefund_remainsBalanced() {
        ledgerService.recordAuthorization(txn);
        ledgerService.recordCapture(txn, 60000L);
        ledgerService.recordCapture(txn, 40000L);
        ledgerService.recordRefund(txn, 30000L);
        assertLedgerBalanced();
    }

    @Test
    @DisplayName("Every operation creates exactly 2 entries (double-entry bookkeeping)")
    void eachOperation_createsExactlyTwoEntries() {
        ledgerService.recordAuthorization(txn);
        assertThat(capturedEntries).hasSize(2);

        capturedEntries.clear();
        ledgerService.recordCapture(txn, 50000L);
        assertThat(capturedEntries).hasSize(2);
    }

    private void assertLedgerBalanced() {
        long totalDebits = capturedEntries.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.DEBIT)
                .mapToLong(LedgerEntry::getAmount)
                .sum();

        long totalCredits = capturedEntries.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.CREDIT)
                .mapToLong(LedgerEntry::getAmount)
                .sum();

        assertThat(totalDebits)
                .as("Total DEBITs must equal total CREDITs (double-entry invariant)")
                .isEqualTo(totalCredits);
    }
}
