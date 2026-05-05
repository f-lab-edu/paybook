package com.paybook.core.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String couponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType couponType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    private int discountValue;

    private Integer maxDiscountAmount;

    private Integer minOrderAmount;

    public CouponEntity(String couponId, CouponStatus status, CouponType couponType,
                        DiscountType discountType, int discountValue, Integer maxDiscountAmount) {
        this(couponId, status, couponType, discountType, discountValue, maxDiscountAmount, null);
    }

    public CouponEntity(String couponId, CouponStatus status, CouponType couponType,
                        DiscountType discountType, int discountValue, Integer maxDiscountAmount,
                        Integer minOrderAmount) {
        this.couponId = couponId;
        this.status = status;
        this.couponType = couponType;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.minOrderAmount = minOrderAmount;
    }

    private static final int PERCENT_DIVISOR = 100;

    public int calculateDiscount(int orderAmount) {
        if (discountType == DiscountType.FIXED_AMOUNT) {
            return Math.min(discountValue, orderAmount);
        }
        int calculated = orderAmount * discountValue / PERCENT_DIVISOR;
        if (maxDiscountAmount != null) {
            calculated = Math.min(calculated, maxDiscountAmount);
        }
        return Math.min(calculated, orderAmount);
    }

    public void markUsed() {
        this.status = CouponStatus.USED;
    }

    public void restore() {
        this.status = CouponStatus.ACTIVE;
    }
}
