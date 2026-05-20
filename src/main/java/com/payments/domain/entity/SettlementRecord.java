package com.payments.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "settlement_records")
public class SettlementRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "total_transactions", nullable = false)
    private Integer totalTransactions = 0;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount = 0L;

    @Column(name = "fee_amount", nullable = false)
    private Long feeAmount = 0L;

    @Column(name = "net_amount", nullable = false)
    private Long netAmount = 0L;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public SettlementRecord() {}

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final SettlementRecord s = new SettlementRecord();
        public Builder merchant(Merchant v) { s.merchant = v; return this; }
        public Builder settlementDate(LocalDate v) { s.settlementDate = v; return this; }
        public Builder totalTransactions(Integer v) { s.totalTransactions = v; return this; }
        public Builder totalAmount(Long v) { s.totalAmount = v; return this; }
        public Builder feeAmount(Long v) { s.feeAmount = v; return this; }
        public Builder netAmount(Long v) { s.netAmount = v; return this; }
        public Builder status(String v) { s.status = v; return this; }
        public Builder processedAt(LocalDateTime v) { s.processedAt = v; return this; }
        public SettlementRecord build() { return s; }
    }

    public UUID getId() { return id; }
    public Merchant getMerchant() { return merchant; }
    public LocalDate getSettlementDate() { return settlementDate; }
    public Integer getTotalTransactions() { return totalTransactions; }
    public Long getTotalAmount() { return totalAmount; }
    public Long getFeeAmount() { return feeAmount; }
    public Long getNetAmount() { return netAmount; }
    public String getStatus() { return status; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
