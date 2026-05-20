package com.payments.domain.entity;

import com.payments.domain.enums.LedgerEntryType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private LedgerEntryType entryType;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private Long amount;

    @Column(length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public LedgerEntry() {}

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final LedgerEntry e = new LedgerEntry();
        public Builder transaction(Transaction v) { e.transaction = v; return this; }
        public Builder entryType(LedgerEntryType v) { e.entryType = v; return this; }
        public Builder accountType(String v) { e.accountType = v; return this; }
        public Builder accountId(UUID v) { e.accountId = v; return this; }
        public Builder amount(Long v) { e.amount = v; return this; }
        public Builder description(String v) { e.description = v; return this; }
        public LedgerEntry build() { return e; }
    }

    public UUID getId() { return id; }
    public Transaction getTransaction() { return transaction; }
    public LedgerEntryType getEntryType() { return entryType; }
    public String getAccountType() { return accountType; }
    public UUID getAccountId() { return accountId; }
    public Long getAmount() { return amount; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
