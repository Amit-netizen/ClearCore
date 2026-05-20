package com.payments.api.controller;

import com.payments.api.request.AuthorizationRequest;
import com.payments.api.request.CaptureRequest;
import com.payments.api.request.ReversalRequest;
import com.payments.api.response.ApiResponse;
import com.payments.api.response.TransactionResponse;
import com.payments.domain.entity.Card;
import com.payments.domain.entity.Transaction;
import com.payments.repository.CardRepository;
import com.payments.repository.TransactionRepository;
import com.payments.service.AuthorizationService;
import com.payments.service.CaptureService;
import com.payments.service.RateLimiterService;
import com.payments.service.ReversalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final AuthorizationService authorizationService;
    private final CaptureService captureService;
    private final ReversalService reversalService;
    private final RateLimiterService rateLimiterService;
    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;

    /**
     * POST /api/v1/transactions/authorize
     *
     * Headers:
     *   X-Idempotency-Key: <uuid>  — optional, enables safe retries
     *   X-Merchant-Id: <uuid>      — used for rate limiting
     */
    @PostMapping("/authorize")
    public ResponseEntity<ApiResponse<TransactionResponse>> authorize(
            @Valid @RequestBody AuthorizationRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
            HttpServletRequest httpRequest) {

        // Rate limiting per merchant
        String rateLimitKey = merchantIdHeader != null
                ? merchantIdHeader
                : request.getMerchantId().toString();
        RateLimiterService.RateLimitResult rateLimit = rateLimiterService.checkRateLimit(rateLimitKey);

        if (!rateLimit.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("X-RateLimit-Remaining", String.valueOf(rateLimit.remaining()))
                    .header("Retry-After", String.valueOf(rateLimit.retryAfterSeconds()))
                    .body(ApiResponse.error("Rate limit exceeded", "RATE_LIMIT_EXCEEDED"));
        }

        // CVV validation before entering the transactional path
        Optional<Card> cardOpt = cardRepository.findById(request.getCardId());
        if (cardOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ApiResponse.error("Card not found", "CARD_NOT_FOUND"));
        }

        if (!authorizationService.validateCvv(cardOpt.get(), request.getCvv())) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .header("X-RateLimit-Remaining", String.valueOf(rateLimit.remaining()))
                    .body(ApiResponse.error("CVV validation failed", "82"));
        }

        Transaction txn = authorizationService.authorize(
                request.getCardId(),
                request.getMerchantId(),
                request.getAmount(),
                request.getCurrency(),
                idempotencyKey,
                getClientIp(httpRequest)
        );

        TransactionResponse response = TransactionResponse.from(txn);
        HttpStatus status = txn.getResponseCode().isApproved()
                ? HttpStatus.CREATED
                : HttpStatus.UNPROCESSABLE_ENTITY;

        return ResponseEntity.status(status)
                .header("X-RateLimit-Remaining", String.valueOf(rateLimit.remaining()))
                .body(ApiResponse.ok(response, txn.getResponseCode().getMessage()));
    }

    /**
     * POST /api/v1/transactions/{id}/capture
     */
    @PostMapping("/{id}/capture")
    public ResponseEntity<ApiResponse<TransactionResponse>> capture(
            @PathVariable UUID id,
            @Valid @RequestBody CaptureRequest request) {

        Transaction txn = captureService.capture(id, request.getAmount());
        return ResponseEntity.ok(ApiResponse.ok(TransactionResponse.from(txn)));
    }

    /**
     * POST /api/v1/transactions/{id}/reverse
     *
     * Pre-capture = VOID, Post-capture = REFUND.
     * Amount is optional — defaults to full reversal.
     */
    @PostMapping("/{id}/reverse")
    public ResponseEntity<ApiResponse<TransactionResponse>> reverse(
            @PathVariable UUID id,
            @RequestBody(required = false) ReversalRequest request) {

        Long amount = request != null ? request.getAmount() : null;
        Transaction txn = reversalService.reverse(id, amount);
        return ResponseEntity.ok(ApiResponse.ok(TransactionResponse.from(txn)));
    }

    /**
     * GET /api/v1/transactions/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(@PathVariable UUID id) {
        return transactionRepository.findById(id)
                .map(txn -> ResponseEntity.ok(ApiResponse.ok(TransactionResponse.from(txn))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Transaction not found", "NOT_FOUND")));
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isEmpty())
                ? xff.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}
