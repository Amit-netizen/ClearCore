package com.payments.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "event_log")
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(nullable = false)
    private Boolean processed = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public EventLog() {}

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final EventLog e = new EventLog();
        public Builder eventType(String v) { e.eventType = v; return this; }
        public Builder transactionId(UUID v) { e.transactionId = v; return this; }
        public Builder payload(Map<String, Object> v) { e.payload = v; return this; }
        public Builder processed(Boolean v) { e.processed = v; return this; }
        public EventLog build() { return e; }
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public UUID getTransactionId() { return transactionId; }
    public Map<String, Object> getPayload() { return payload; }
    public Boolean getProcessed() { return processed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setProcessed(Boolean v) { this.processed = v; }
}
