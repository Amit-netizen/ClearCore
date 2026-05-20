package com.payments.repository;

import com.payments.domain.entity.Transaction;
import com.payments.domain.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findByStatus(TransactionStatus status);

    List<Transaction> findByMerchantIdAndStatus(UUID merchantId, TransactionStatus status);

    @Query("SELECT t FROM Transaction t WHERE t.merchant.id = :merchantId " +
           "AND t.status = :status " +
           "AND t.createdAt BETWEEN :from AND :to")
    List<Transaction> findByMerchantAndStatusBetween(
            @Param("merchantId") UUID merchantId,
            @Param("status") TransactionStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.card.id = :cardId " +
           "AND t.createdAt > :since")
    long countByCardIdAndCreatedAtAfter(@Param("cardId") UUID cardId,
                                        @Param("since") LocalDateTime since);

    Page<Transaction> findByMerchantId(UUID merchantId, Pageable pageable);
}
