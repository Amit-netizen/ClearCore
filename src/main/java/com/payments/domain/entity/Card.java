package com.payments.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "card_number", nullable = false, unique = true)
    private String cardNumber;

    @Column(name = "card_holder_name", nullable = false)
    private String cardHolderName;

    @Column(name = "expiry_month", nullable = false)
    private Integer expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private Integer expiryYear;

    @Column(name = "cvv_hash", nullable = false)
    private String cvvHash;

    @Column(name = "available_balance", nullable = false)
    private Long availableBalance;

    @Column(name = "credit_limit", nullable = false)
    private Long creditLimit;

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public Card() {}

    // Builder pattern
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Card card = new Card();
        public Builder id(UUID id) { card.id = id; return this; }
        public Builder cardNumber(String v) { card.cardNumber = v; return this; }
        public Builder cardHolderName(String v) { card.cardHolderName = v; return this; }
        public Builder expiryMonth(Integer v) { card.expiryMonth = v; return this; }
        public Builder expiryYear(Integer v) { card.expiryYear = v; return this; }
        public Builder cvvHash(String v) { card.cvvHash = v; return this; }
        public Builder availableBalance(Long v) { card.availableBalance = v; return this; }
        public Builder creditLimit(Long v) { card.creditLimit = v; return this; }
        public Builder active(Boolean v) { card.active = v; return this; }
        public Builder version(Long v) { card.version = v; return this; }
        public Card build() { return card; }
    }

    // Business logic
    public boolean isExpired() {
        LocalDateTime now = LocalDateTime.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();
        if (expiryYear < currentYear) return true;
        if (expiryYear == currentYear && expiryMonth < currentMonth) return true;
        return false;
    }

    public boolean hasSufficientBalance(long amount) { return availableBalance >= amount; }

    public void deductBalance(long amount) {
        if (amount > availableBalance)
            throw new IllegalStateException("Insufficient balance: available=" + availableBalance + ", requested=" + amount);
        this.availableBalance -= amount;
    }

    public void restoreBalance(long amount) { this.availableBalance += amount; }

    // Getters and Setters
    public UUID getId() { return id; }
    public String getCardNumber() { return cardNumber; }
    public String getCardHolderName() { return cardHolderName; }
    public Integer getExpiryMonth() { return expiryMonth; }
    public Integer getExpiryYear() { return expiryYear; }
    public String getCvvHash() { return cvvHash; }
    public Long getAvailableBalance() { return availableBalance; }
    public Long getCreditLimit() { return creditLimit; }
    public Boolean getActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
    public void setId(UUID id) { this.id = id; }
    public void setAvailableBalance(Long v) { this.availableBalance = v; }
    public void setActive(Boolean v) { this.active = v; }
    public void setExpiryMonth(Integer v) { this.expiryMonth = v; }
    public void setExpiryYear(Integer v) { this.expiryYear = v; }
    public void setVersion(Long v) { this.version = v; }
}
