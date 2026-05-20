package com.payments.kafka;

import com.payments.domain.enums.ResponseCode;
import com.payments.domain.enums.TransactionStatus;
import com.payments.domain.entity.Transaction;

import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionEvent(
        UUID eventId,
        String eventType,
        UUID transactionId,
        UUID cardId,
        UUID merchantId,
        Long amount,
        String currency,
        TransactionStatus status,
        ResponseCode responseCode,
        String responseMessage,
        Integer fraudScore,
        LocalDateTime occurredAt
) {
    public static TransactionEvent from(Transaction txn, String eventType) {
        return new TransactionEvent(
                UUID.randomUUID(),
                eventType,
                txn.getId(),
                txn.getCard().getId(),
                txn.getMerchant().getId(),
                txn.getAmount(),
                txn.getCurrency(),
                txn.getStatus(),
                txn.getResponseCode(),
                txn.getResponseMessage(),
                txn.getFraudScore(),
                LocalDateTime.now()
        );
    }
}
