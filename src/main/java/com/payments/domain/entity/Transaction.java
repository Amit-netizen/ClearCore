package com.payments.domain.entity;

import com.payments.domain.enums.ResponseCode;
import com.payments.domain.enums.TransactionStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_code", nullable = false)
    private ResponseCode responseCode;

    @Column(name = "response_message")
    private String responseMessage;

    @Column(name = "authorized_amount")
    private Long authorizedAmount;

    @Column(name = "captured_amount")
    private Long capturedAmount = 0L;

    @Column(name = "reversed_amount")
    private Long reversedAmount = 0L;

    @Column(name = "fraud_score")
    private Integer fraudScore = 0;

    @Column(name = "fraud_flags")
    private String fraudFlags;

    @Column(name = "ip_address")
    private String ipAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public Transaction() {}

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Transaction t = new Transaction();
        public Builder id(UUID v) { t.id = v; return this; }
        public Builder idempotencyKey(String v) { t.idempotencyKey = v; return this; }
        public Builder card(Card v) { t.card = v; return this; }
        public Builder merchant(Merchant v) { t.merchant = v; return this; }
        public Builder amount(Long v) { t.amount = v; return this; }
        public Builder currency(String v) { t.currency = v; return this; }
        public Builder status(TransactionStatus v) { t.status = v; return this; }
        public Builder responseCode(ResponseCode v) { t.responseCode = v; return this; }
        public Builder responseMessage(String v) { t.responseMessage = v; return this; }
        public Builder authorizedAmount(Long v) { t.authorizedAmount = v; return this; }
        public Builder capturedAmount(Long v) { t.capturedAmount = v; return this; }
        public Builder reversedAmount(Long v) { t.reversedAmount = v; return this; }
        public Builder fraudScore(Integer v) { t.fraudScore = v; return this; }
        public Builder fraudFlags(String v) { t.fraudFlags = v; return this; }
        public Builder ipAddress(String v) { t.ipAddress = v; return this; }
        public Builder version(Long v) { t.version = v; return this; }
        public Transaction build() { return t; }
    }

    public long getRemainingAuthorizedAmount() {
        if (authorizedAmount == null) return 0;
        return authorizedAmount - capturedAmount - reversedAmount;
    }

    public boolean canCapture() {
        return status == TransactionStatus.AUTHORIZED || status == TransactionStatus.PARTIALLY_CAPTURED;
    }

    public boolean canReverse() {
        return status == TransactionStatus.AUTHORIZED
                || status == TransactionStatus.PARTIALLY_CAPTURED
                || status == TransactionStatus.CAPTURED;
    }

    // Getters
    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Card getCard() { return card; }
    public Merchant getMerchant() { return merchant; }
    public Long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public TransactionStatus getStatus() { return status; }
    public ResponseCode getResponseCode() { return responseCode; }
    public String getResponseMessage() { return responseMessage; }
    public Long getAuthorizedAmount() { return authorizedAmount; }
    public Long getCapturedAmount() { return capturedAmount; }
    public Long getReversedAmount() { return reversedAmount; }
    public Integer getFraudScore() { return fraudScore; }
    public String getFraudFlags() { return fraudFlags; }
    public String getIpAddress() { return ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    // Setters
    public void setStatus(TransactionStatus v) { this.status = v; }
    public void setResponseCode(ResponseCode v) { this.responseCode = v; }
    public void setResponseMessage(String v) { this.responseMessage = v; }
    public void setAuthorizedAmount(Long v) { this.authorizedAmount = v; }
    public void setCapturedAmount(Long v) { this.capturedAmount = v; }
    public void setReversedAmount(Long v) { this.reversedAmount = v; }
    public void setFraudScore(Integer v) { this.fraudScore = v; }
    public void setFraudFlags(String v) { this.fraudFlags = v; }
    public void setVersion(Long v) { this.version = v; }
}
