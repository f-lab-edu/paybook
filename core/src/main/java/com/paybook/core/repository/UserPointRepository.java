package com.paybook.core.repository;

import com.paybook.core.entity.UserPointEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserPointRepository extends JpaRepository<UserPointEntity, Long> {

    Optional<UserPointEntity> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserPointEntity u WHERE u.userId = :userId")
    Optional<UserPointEntity> findByUserIdForUpdate(@Param("userId") String userId);
}
