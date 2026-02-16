package com.checkout_service.repo;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.checkout_service.domain.CheckoutOutboxEvent;
import com.checkout_service.domain.OutboxStatus;

import jakarta.persistence.LockModeType;

public interface CheckoutOutboxRepository
        extends JpaRepository<CheckoutOutboxEvent, Long> {

    List<CheckoutOutboxEvent> 
        findTop100ByStatusInOrderByCreatedAtAsc(List<String> status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT e FROM CheckoutOutboxEvent e
    WHERE e.status IN :statuses
    ORDER BY e.createdAt ASC
    """)
    List<CheckoutOutboxEvent> findBatchForUpdate(
        @Param("statuses") List<OutboxStatus> statuses,
        Pageable pageable);

    List<CheckoutOutboxEvent>
    findByStatusAndProcessedAtIsNullAndCreatedAtBefore(
        OutboxStatus status,
        Instant time);
}
