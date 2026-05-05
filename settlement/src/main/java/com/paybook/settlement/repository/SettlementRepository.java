package com.paybook.settlement.repository;

import com.paybook.settlement.entity.SettlementEntity;
import com.paybook.settlement.entity.SettlementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<SettlementEntity, Long> {

    Optional<SettlementEntity> findBySettlementId(String settlementId);

    Optional<SettlementEntity> findByOrderId(String orderId);

    Page<SettlementEntity> findBySellerIdOrderByCreatedAtDesc(String sellerId, Pageable pageable);

    Page<SettlementEntity> findBySellerIdAndStatusOrderByCreatedAtDesc(
            String sellerId, SettlementStatus status, Pageable pageable);

    Page<SettlementEntity> findByStatusOrderByCreatedAtAsc(SettlementStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SettlementEntity s WHERE s.status = :status ORDER BY s.createdAt ASC")
    Page<SettlementEntity> findByStatusForUpdate(@Param("status") SettlementStatus status, Pageable pageable);

    Page<SettlementEntity> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            SettlementStatus status, LocalDateTime cutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SettlementEntity s WHERE s.status = :status AND s.createdAt < :cutoff ORDER BY s.createdAt ASC")
    Page<SettlementEntity> findByStatusAndCreatedAtBeforeForUpdate(
            @Param("status") SettlementStatus status, @Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(s.orderAmount), 0) AS totalOrderAmount,
                   COALESCE(SUM(s.commissionAmount), 0) AS totalCommissionAmount,
                   COALESCE(SUM(s.settlementAmount), 0) AS totalSettlementAmount,
                   COUNT(s) AS count
            FROM SettlementEntity s
            WHERE s.sellerId = :sellerId
            """)
    SettlementSummaryProjection getSellerSummary(@Param("sellerId") String sellerId);

    @Query("""
            SELECT COALESCE(SUM(s.orderAmount), 0) AS totalOrderAmount,
                   COALESCE(SUM(s.commissionAmount), 0) AS totalCommissionAmount,
                   COALESCE(SUM(s.settlementAmount), 0) AS totalSettlementAmount,
                   COUNT(s) AS count
            FROM SettlementEntity s
            WHERE s.sellerId = :sellerId AND s.status = :status
            """)
    SettlementSummaryProjection getSellerSummaryByStatus(
            @Param("sellerId") String sellerId, @Param("status") SettlementStatus status);

    interface SettlementSummaryProjection {
        int getTotalOrderAmount();
        int getTotalCommissionAmount();
        int getTotalSettlementAmount();
        Long getCount();
    }
}
