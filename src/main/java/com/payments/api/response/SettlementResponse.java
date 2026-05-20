package com.payments.api.response;

import com.payments.domain.entity.SettlementRecord;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class SettlementResponse {

    private UUID settlementId;
    private UUID merchantId;
    private LocalDate settlementDate;
    private Integer totalTransactions;
    private Long totalAmount;
    private Long feeAmount;
    private Long netAmount;
    private String status;
    private LocalDateTime processedAt;

    public SettlementResponse() {}

    public static SettlementResponse from(SettlementRecord record) {
        SettlementResponse r = new SettlementResponse();
        r.settlementId = record.getId();
        r.merchantId = record.getMerchant().getId();
        r.settlementDate = record.getSettlementDate();
        r.totalTransactions = record.getTotalTransactions();
        r.totalAmount = record.getTotalAmount();
        r.feeAmount = record.getFeeAmount();
        r.netAmount = record.getNetAmount();
        r.status = record.getStatus();
        r.processedAt = record.getProcessedAt();
        return r;
    }

    public UUID getSettlementId() { return settlementId; }
    public UUID getMerchantId() { return merchantId; }
    public LocalDate getSettlementDate() { return settlementDate; }
    public Integer getTotalTransactions() { return totalTransactions; }
    public Long getTotalAmount() { return totalAmount; }
    public Long getFeeAmount() { return feeAmount; }
    public Long getNetAmount() { return netAmount; }
    public String getStatus() { return status; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
