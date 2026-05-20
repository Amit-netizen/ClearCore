package com.payments.api.request;

import jakarta.validation.constraints.*;
import java.util.UUID;

public class AuthorizationRequest {

    @NotNull(message = "Card ID is required")
    private UUID cardId;

    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @Min(value = 1, message = "Amount must be at least 1 paise")
    private long amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3)
    private String currency = "INR";

    @NotBlank(message = "CVV is required")
    @Size(min = 3, max = 4)
    private String cvv;

    public UUID getCardId() { return cardId; }
    public void setCardId(UUID cardId) { this.cardId = cardId; }
    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCvv() { return cvv; }
    public void setCvv(String cvv) { this.cvv = cvv; }
}
