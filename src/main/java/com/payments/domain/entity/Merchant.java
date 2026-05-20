package com.payments.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private Long balance = 0L;

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

    public Merchant() {}

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Merchant m = new Merchant();
        public Builder id(UUID v) { m.id = v; return this; }
        public Builder name(String v) { m.name = v; return this; }
        public Builder email(String v) { m.email = v; return this; }
        public Builder balance(Long v) { m.balance = v; return this; }
        public Builder active(Boolean v) { m.active = v; return this; }
        public Builder version(Long v) { m.version = v; return this; }
        public Merchant build() { return m; }
    }

    public void creditBalance(long amount) { this.balance += amount; }

    public void debitBalance(long amount) {
        if (amount > this.balance) throw new IllegalStateException("Merchant balance insufficient");
        this.balance -= amount;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Long getBalance() { return balance; }
    public Boolean getActive() { return active; }
    public Long getVersion() { return version; }
    public void setId(UUID id) { this.id = id; }
    public void setBalance(Long v) { this.balance = v; }
}
