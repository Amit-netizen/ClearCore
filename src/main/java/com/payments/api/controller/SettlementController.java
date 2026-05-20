package com.payments.api.controller;

import com.payments.api.response.ApiResponse;
import com.payments.api.response.SettlementResponse;
import com.payments.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/merchants")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    /**
     * GET /api/v1/merchants/{merchantId}/settlements
     *
     * Query params:
     *   from=2024-01-01  (default: 30 days ago)
     *   to=2024-12-31    (default: today)
     *   page=0
     *   size=20
     */
    @GetMapping("/{merchantId}/settlements")
    public ResponseEntity<ApiResponse<Page<SettlementResponse>>> getSettlements(
            @PathVariable UUID merchantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LocalDate fromDate = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate toDate = to != null ? to : LocalDate.now();

        PageRequest pageable = PageRequest.of(page, size, Sort.by("settlementDate").descending());
        Page<SettlementResponse> settlements = settlementService
                .getSettlements(merchantId, fromDate, toDate, pageable)
                .map(SettlementResponse::from);

        return ResponseEntity.ok(ApiResponse.ok(settlements));
    }
}
