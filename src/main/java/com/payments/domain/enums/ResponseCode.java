package com.payments.domain.enums;

public enum ResponseCode {
    APPROVED("00", "Approved"),
    REFER_TO_CARD_ISSUER("01", "Refer to card issuer"),
    INVALID_MERCHANT("03", "Invalid merchant"),
    PICK_UP_CARD("04", "Pick up card"),
    DO_NOT_HONOUR("05", "Do not honour"),
    ERROR("06", "Error"),
    PARTIAL_APPROVAL("10", "Partial approval"),
    INVALID_TRANSACTION("12", "Invalid transaction"),
    INVALID_AMOUNT("13", "Invalid amount"),
    INVALID_CARD_NUMBER("14", "Invalid card number"),
    INSUFFICIENT_FUNDS("51", "Insufficient funds"),
    EXPIRED_CARD("54", "Expired card"),
    RESTRICTED_CARD("62", "Restricted card"),
    FRAUD_SUSPECTED("59", "Suspected fraud"),
    CVV_FAIL("82", "CVV fail"),
    DUPLICATE_TRANSACTION("94", "Duplicate transmission"),
    SYSTEM_ERROR("96", "System malfunction"),
    TRANSACTION_NOT_PERMITTED("57", "Transaction not permitted to cardholder");

    private final String code;
    private final String message;

    ResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }

    public boolean isApproved() {
        return "00".equals(this.code);
    }
}
