package com.payments.repository;

import com.payments.domain.entity.SettlementRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, UUID> {

    Page<SettlementRecord> findByMerchantIdAndSettlementDateBetween(
            UUID merchantId, LocalDate from, LocalDate to, Pageable pageable);

    List<SettlementRecord> findByMerchantId(UUID merchantId);
}
