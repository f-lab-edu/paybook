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

    private String exchangeProductId;

    private Integer exchangeQuantity;

    public OrderItemEntity(String productId, int quantity, int price) {
        this.productId = productId;
        this.quantity = quantity;
        this.price = price;
        this.itemStatus = OrderItemStatus.ACTIVE;
    }

    public int getItemTotal() {
        return this.price * this.quantity;
    }

    public void cancelItem() {
        this.itemStatus = OrderItemStatus.CANCELLED;
    }

    public boolean isActive() {
        return this.itemStatus == OrderItemStatus.ACTIVE;
    }

    public void requestExchange(String newProductId, int newQuantity) {
        if (this.itemStatus != OrderItemStatus.ACTIVE) {
            throw new IllegalStateException("활성 상태의 아이템만 교환 요청할 수 있습니다");
        }
        this.itemStatus = OrderItemStatus.EXCHANGE_REQUESTED;
        this.exchangeProductId = newProductId;
        this.exchangeQuantity = newQuantity;
    }

    public void completeExchange() {
        if (this.itemStatus != OrderItemStatus.EXCHANGE_REQUESTED) {
            throw new IllegalStateException("교환 요청 상태의 아이템만 교환 완료할 수 있습니다");
        }
        this.itemStatus = OrderItemStatus.EXCHANGE_COMPLETED;
    }

    public boolean isExchangeRequested() {
        return this.itemStatus == OrderItemStatus.EXCHANGE_REQUESTED;
    }

    void setOrder(OrderEntity order) {
        this.order = order;
    }
}
