package com.payments.api.response;

import com.payments.domain.entity.Transaction;
import com.payments.domain.enums.TransactionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class TransactionResponse {

    private UUID transactionId;
    private String responseCode;
    private String responseMessage;
    private TransactionStatus status;
    private Long amount;
    private Long authorizedAmount;
    private Long capturedAmount;
    private Long reversedAmount;
    private String currency;
    private UUID cardId;
    private UUID merchantId;
    private Integer fraudScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public TransactionResponse() {}

    public static TransactionResponse from(Transaction txn) {
        TransactionResponse r = new TransactionResponse();
        r.transactionId = txn.getId();
        r.responseCode = txn.getResponseCode().getCode();
        r.responseMessage = txn.getResponseCode().getMessage();
        r.status = txn.getStatus();
        r.amount = txn.getAmount();
        r.authorizedAmount = txn.getAuthorizedAmount();
        r.capturedAmount = txn.getCapturedAmount();
        r.reversedAmount = txn.getReversedAmount();
        r.currency = txn.getCurrency();
        r.cardId = txn.getCard().getId();
        r.merchantId = txn.getMerchant().getId();
        r.fraudScore = txn.getFraudScore();
        r.createdAt = txn.getCreatedAt();
        r.updatedAt = txn.getUpdatedAt();
        return r;
    }

    public UUID getTransactionId() { return transactionId; }
    public String getResponseCode() { return responseCode; }
    public String getResponseMessage() { return responseMessage; }
    public TransactionStatus getStatus() { return status; }
    public Long getAmount() { return amount; }
    public Long getAuthorizedAmount() { return authorizedAmount; }
    public Long getCapturedAmount() { return capturedAmount; }
    public Long getReversedAmount() { return reversedAmount; }
    public String getCurrency() { return currency; }
    public UUID getCardId() { return cardId; }
    public UUID getMerchantId() { return merchantId; }
    public Integer getFraudScore() { return fraudScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
