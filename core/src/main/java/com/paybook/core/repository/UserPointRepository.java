package com.paybook.core.repository;

import com.paybook.core.entity.UserPointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPointRepository extends JpaRepository<UserPointEntity, Long> {
    Optional<UserPointEntity> findByUserId(String userId);
}
