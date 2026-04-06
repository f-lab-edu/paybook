package com.paybook.core.repository;

import com.paybook.core.entity.CouponEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<CouponEntity, Long> {

    Optional<CouponEntity> findByCouponId(String couponId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponEntity c WHERE c.couponId = :couponId")
    Optional<CouponEntity> findByCouponIdForUpdate(@Param("couponId") String couponId);
}
