package com.payments.api.request;

import jakarta.validation.constraints.Min;

public class CaptureRequest {
    @Min(value = 1, message = "Capture amount must be at least 1 paise")
    private long amount;
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
}
