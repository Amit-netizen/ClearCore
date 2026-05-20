package com.payments.service;

import com.payments.domain.entity.LedgerEntry;
import com.payments.domain.entity.Transaction;
import com.payments.domain.enums.LedgerEntryType;
import com.payments.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * Double-entry bookkeeping invariant:
     * SUM(all DEBIT amounts) == SUM(all CREDIT amounts) at all times.
     */

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAuthorization(Transaction txn) {
        // Card balance debited (funds held)
        save(txn, LedgerEntryType.DEBIT, "CARD_BALANCE", txn.getCard().getId(),
                txn.getAmount(), "Authorization hold: " + txn.getId());
        // Corresponding credit to held-funds liability
        save(txn, LedgerEntryType.CREDIT, "HELD_FUNDS", txn.getMerchant().getId(),
                txn.getAmount(), "Pending funds for txn: " + txn.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCapture(Transaction txn, long captureAmount) {
        save(txn, LedgerEntryType.DEBIT, "HELD_FUNDS", txn.getMerchant().getId(),
                captureAmount, "Capture from hold: " + txn.getId());
        save(txn, LedgerEntryType.CREDIT, "MERCHANT_BALANCE", txn.getMerchant().getId(),
                captureAmount, "Merchant credit for capture: " + txn.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordVoid(Transaction txn, long voidAmount) {
        save(txn, LedgerEntryType.DEBIT, "HELD_FUNDS", txn.getMerchant().getId(),
                voidAmount, "Void — releasing hold: " + txn.getId());
        save(txn, LedgerEntryType.CREDIT, "CARD_BALANCE", txn.getCard().getId(),
                voidAmount, "Card balance restored (void): " + txn.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRefund(Transaction txn, long refundAmount) {
        save(txn, LedgerEntryType.DEBIT, "MERCHANT_BALANCE", txn.getMerchant().getId(),
                refundAmount, "Refund from merchant: " + txn.getId());
        save(txn, LedgerEntryType.CREDIT, "CARD_BALANCE", txn.getCard().getId(),
                refundAmount, "Card balance restored (refund): " + txn.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSettlement(Transaction txn) {
        save(txn, LedgerEntryType.DEBIT, "MERCHANT_BALANCE", txn.getMerchant().getId(),
                txn.getCapturedAmount(), "Settlement batch: " + txn.getId());
        save(txn, LedgerEntryType.CREDIT, "SETTLED_FUNDS", txn.getMerchant().getId(),
                txn.getCapturedAmount(), "Settled: " + txn.getId());
    }

    public long getTotalDebits() {
        Long result = ledgerEntryRepository.sumByEntryType(LedgerEntryType.DEBIT);
        return result != null ? result : 0L;
    }

    public long getTotalCredits() {
        Long result = ledgerEntryRepository.sumByEntryType(LedgerEntryType.CREDIT);
        return result != null ? result : 0L;
    }

    private void save(Transaction txn, LedgerEntryType type, String accountType,
                      UUID accountId, long amount, String description) {
        LedgerEntry entry = LedgerEntry.builder()
                .transaction(txn)
                .entryType(type)
                .accountType(accountType)
                .accountId(accountId)
                .amount(amount)
                .description(description)
                .build();
        ledgerEntryRepository.save(entry);
    }
}
