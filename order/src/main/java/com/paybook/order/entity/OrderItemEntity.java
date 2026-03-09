package com.paybook.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String productId;

    private int quantity;

    private int price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderItemStatus itemStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    public OrderItemEntity(String productId, int quantity, int price) {
        this.productId = productId;
        this.quantity = quantity;
        this.price = price;
        this.itemStatus = OrderItemStatus.ACTIVE;
    }

    public void cancelItem() {
        this.itemStatus = OrderItemStatus.CANCELLED;
    }

    public boolean isActive() {
        return this.itemStatus == OrderItemStatus.ACTIVE;
    }

    void setOrder(OrderEntity order) {
        this.order = order;
    }
}
