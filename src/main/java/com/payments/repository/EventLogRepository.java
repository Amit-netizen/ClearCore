package com.payments.repository;

import com.payments.domain.entity.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventLogRepository extends JpaRepository<EventLog, UUID> {
    List<EventLog> findByTransactionId(UUID transactionId);
    List<EventLog> findByProcessedFalse();
}
