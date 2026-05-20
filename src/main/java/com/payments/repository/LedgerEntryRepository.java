package com.payments.repository;

import com.payments.domain.entity.LedgerEntry;
import com.payments.domain.enums.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l WHERE l.entryType = :type")
    Long sumByEntryType(@Param("type") LedgerEntryType type);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l " +
           "WHERE l.accountId = :accountId AND l.entryType = :type")
    Long sumByAccountIdAndEntryType(@Param("accountId") UUID accountId,
                                    @Param("type") LedgerEntryType type);
}
