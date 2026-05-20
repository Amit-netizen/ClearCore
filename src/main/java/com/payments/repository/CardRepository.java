package com.payments.repository;

import com.payments.domain.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardRepository extends JpaRepository<Card, UUID> {

    Optional<Card> findByCardNumber(String cardNumber);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT c FROM Card c WHERE c.id = :id")
    Optional<Card> findByIdWithOptimisticLock(UUID id);
}
