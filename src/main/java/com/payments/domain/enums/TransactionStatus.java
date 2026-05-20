package com.payments.domain.enums;

public enum TransactionStatus {
    PENDING,
    AUTHORIZED,
    CAPTURED,
    PARTIALLY_CAPTURED,
    DECLINED,
    REVERSED,
    REFUNDED,
    SETTLED
}
