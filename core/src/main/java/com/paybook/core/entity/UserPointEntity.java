package com.paybook.core.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_points")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    private int balance;

    public UserPointEntity(String userId, int balance) {
        this.userId = userId;
        this.balance = balance;
    }

    public void deduct(int amount) {
        if (this.balance < amount) {
            throw new IllegalStateException("포인트가 부족합니다");
        }
        this.balance -= amount;
    }

    public void restore(int amount) {
        this.balance += amount;
    }
}
