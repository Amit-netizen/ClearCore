package com.payments.service;

import com.payments.domain.entity.SettlementRecord;
import com.payments.repository.SettlementRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class SettlementService {

    private final SettlementRecordRepository settlementRecordRepository;

    public SettlementService(SettlementRecordRepository settlementRecordRepository) {
        this.settlementRecordRepository = settlementRecordRepository;
    }

    @Transactional(readOnly = true)
    public Page<SettlementRecord> getSettlements(UUID merchantId, LocalDate from,
                                                  LocalDate to, Pageable pageable) {
        return settlementRecordRepository
                .findByMerchantIdAndSettlementDateBetween(merchantId, from, to, pageable);
    }
}
