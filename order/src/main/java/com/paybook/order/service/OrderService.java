package com.paybook.order.service;

import com.paybook.core.entity.CouponEntity;
import com.paybook.core.entity.CouponStatus;
import com.paybook.core.entity.ProductEntity;
import com.paybook.core.entity.UserPointEntity;
import com.paybook.core.repository.CouponRepository;
import com.paybook.core.repository.ProductRepository;
import com.paybook.core.repository.UserPointRepository;
import com.paybook.order.config.DeliveryFeeConfig;
import com.paybook.order.config.DiscountPolicyConfig;
import com.paybook.order.dto.CreateOrderRequest;
import com.paybook.order.dto.OrderResponse;
import com.paybook.order.dto.OrderResponse.OrderItemResponse;
import com.paybook.order.entity.OrderEntity;
import com.paybook.order.entity.OrderItemEntity;
import com.paybook.order.entity.OrderStatus;
import com.paybook.order.exception.OrderException;
import com.paybook.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int PERCENT_DIVISOR = 100;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final UserPointRepository userPointRepository;
    private final DiscountPolicyConfig discountPolicyConfig;
    private final DeliveryFeeConfig deliveryFeeConfig;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        List<ProductEntity> products = validateAndFetchProducts(request);
        CouponEntity coupon = validateCoupon(request.couponId());
        UserPointEntity userPoint = validatePoints(request);

        int totalAmount = calculateTotalAmount(request, products);
        int couponDiscountAmount = calculateCouponDiscount(coupon, totalAmount);
        int pointDiscountAmount = resolvePointAmount(request);

        validateDiscountLimit(totalAmount, couponDiscountAmount, pointDiscountAmount);

        int deliveryFee = calculateDeliveryFee(totalAmount);
        int pgPaymentAmount = totalAmount - couponDiscountAmount - pointDiscountAmount + deliveryFee;

        validateMinPgPayment(totalAmount, pgPaymentAmount, deliveryFee);

        OrderEntity order = buildAndSaveOrder(
                request, products, totalAmount, couponDiscountAmount,
                pointDiscountAmount, pgPaymentAmount, deliveryFee);

        applyResourceDeductions(products, request, coupon, userPoint);

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        OrderEntity order = findOrderOrThrow(orderId);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByUserId(String userId, OrderStatus status, Pageable pageable) {
        Page<OrderEntity> orders = (status != null)
                ? orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable)
                : orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return orders.map(this::toResponse);
    }

    @Transactional
    public OrderResponse confirmOrder(String orderId) {
        OrderEntity order = findOrderOrThrow(orderId);
        order.confirm();
        return toResponse(order);
    }

    @Transactional
    public OrderResponse markPaymentFailed(String orderId) {
        OrderEntity order = findOrderOrThrow(orderId);
        order.markPaymentFailed();
        restoreResources(order);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse startShipping(String orderId) {
        OrderEntity order = findOrderOrThrow(orderId);
        order.startShipping();
        return toResponse(order);
    }

    @Transactional
    public OrderResponse markDelivered(String orderId) {
        OrderEntity order = findOrderOrThrow(orderId);
        order.markDelivered();
        return toResponse(order);
    }

    @Transactional
    public OrderResponse confirmPurchase(String orderId) {
        OrderEntity order = findOrderOrThrow(orderId);
        order.confirmPurchase();
        return toResponse(order);
    }

    @Transactional
    public OrderResponse requestReturn(String orderId) {
        OrderEntity order = findOrderOrThrow(orderId);
        order.requestReturn();
        return toResponse(order);
    }

    @Transactional
    public OrderResponse completeReturn(String orderId) {
        OrderEntity order = findOrderOrThrow(orderId);
        order.completeReturn();
        restoreResources(order);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(String orderId) {
        OrderEntity order = findOrderOrThrow(orderId);
        restoreResources(order);
        order.cancel();
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancelItem(String orderId, String productId) {
        OrderEntity order = findOrderOrThrow(orderId);

        if (!order.isCancellable()) {
            throw OrderException.orderNotCancellable(orderId);
        }

        OrderItemEntity targetItem = order.getItems().stream()
                .filter(item -> item.getProductId().equals(productId) && item.isActive())
                .findFirst()
                .orElseThrow(() -> OrderException.productNotFound(productId));

        targetItem.cancelItem();

        productRepository.findByProductId(productId)
                .orElseThrow(() -> OrderException.productNotFound(productId))
                .restoreStock(targetItem.getQuantity());

        boolean allCancelled = order.getItems().stream().noneMatch(OrderItemEntity::isActive);
        if (allCancelled) {
            restoreCoupon(order);
            restorePoints(order);
            order.cancel();
        }

        return toResponse(order);
    }

    // ── 검증 메서드 ──

    private List<ProductEntity> validateAndFetchProducts(CreateOrderRequest request) {
        return request.items().stream()
                .map(itemReq -> {
                    ProductEntity product = productRepository.findByProductId(itemReq.productId())
                            .orElseThrow(() -> OrderException.productNotFound(itemReq.productId()));
                    if (product.getStockQuantity() < itemReq.quantity()) {
                        throw OrderException.outOfStock();
                    }
                    return product;
                })
                .toList();
    }

    private CouponEntity validateCoupon(String couponId) {
        if (couponId == null) {
            return null;
        }
        CouponEntity coupon = couponRepository.findByCouponId(couponId)
                .orElseThrow(OrderException::couponNotFound);

        if (coupon.getStatus() == CouponStatus.USED) {
            throw OrderException.couponAlreadyUsed();
        }
        if (coupon.getStatus() == CouponStatus.EXPIRED) {
            throw OrderException.couponExpired();
        }
        return coupon;
    }

    private UserPointEntity validatePoints(CreateOrderRequest request) {
        if (request.pointAmountToUse() == null || request.pointAmountToUse() <= 0) {
            return null;
        }
        UserPointEntity userPoint = userPointRepository.findByUserId(request.userId())
                .orElseThrow(OrderException::pointsUnavailable);

        if (userPoint.getBalance() < request.pointAmountToUse()) {
            throw OrderException.pointsUnavailable();
        }
        return userPoint;
    }

    // ── 계산 메서드 ──

    private int calculateTotalAmount(CreateOrderRequest request, List<ProductEntity> products) {
        return IntStream.range(0, request.items().size())
                .map(i -> products.get(i).getPrice() * request.items().get(i).quantity())
                .sum();
    }

    private int calculateCouponDiscount(CouponEntity coupon, int totalAmount) {
        if (coupon == null) {
            return 0;
        }
        if (coupon.getMinOrderAmount() != null && totalAmount < coupon.getMinOrderAmount()) {
            throw OrderException.couponMinOrderAmountNotMet(coupon.getMinOrderAmount());
        }
        return coupon.calculateDiscount(totalAmount);
    }

    private int resolvePointAmount(CreateOrderRequest request) {
        return (request.pointAmountToUse() != null && request.pointAmountToUse() > 0)
                ? request.pointAmountToUse() : 0;
    }

    private void validateDiscountLimit(int totalAmount, int couponDiscount, int pointDiscount) {
        int maxDiscount = totalAmount * discountPolicyConfig.maxDiscountPercent() / PERCENT_DIVISOR;
        if ((couponDiscount + pointDiscount) > maxDiscount) {
            throw OrderException.discountLimitExceeded();
        }
    }

    private int calculateDeliveryFee(int totalAmount) {
        return (totalAmount >= deliveryFeeConfig.freeThreshold()) ? 0 : deliveryFeeConfig.fee();
    }

    private void validateMinPgPayment(int totalAmount, int pgPaymentAmount, int deliveryFee) {
        int minPgPayment = totalAmount * discountPolicyConfig.minPgPaymentPercent() / PERCENT_DIVISOR;
        if ((pgPaymentAmount - deliveryFee) < minPgPayment) {
            throw OrderException.pgPaymentBelowMinimum();
        }
    }

    // ── 엔티티 생성/저장 ──

    private OrderEntity buildAndSaveOrder(CreateOrderRequest request, List<ProductEntity> products,
                                          int totalAmount, int couponDiscount, int pointDiscount,
                                          int pgPaymentAmount, int deliveryFee) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);

        OrderEntity order = OrderEntity.builder()
                .orderId(orderId)
                .userId(request.userId())
                .totalAmount(totalAmount)
                .couponDiscountAmount(couponDiscount)
                .pointDiscountAmount(pointDiscount)
                .pgPaymentAmount(pgPaymentAmount)
                .deliveryFee(deliveryFee)
                .deliveryAddress(request.deliveryAddress())
                .couponId(request.couponId())
                .pointAmountToUse(request.pointAmountToUse())
                .build();

        IntStream.range(0, request.items().size()).forEach(i -> {
            CreateOrderRequest.OrderItemRequest itemReq = request.items().get(i);
            order.addItem(new OrderItemEntity(itemReq.productId(), itemReq.quantity(), products.get(i).getPrice()));
        });

        return orderRepository.save(order);
    }

    private void applyResourceDeductions(List<ProductEntity> products, CreateOrderRequest request,
                                         CouponEntity coupon, UserPointEntity userPoint) {
        IntStream.range(0, products.size())
                .forEach(i -> products.get(i).deductStock(request.items().get(i).quantity()));

        if (coupon != null) {
            coupon.markUsed();
        }
        if (userPoint != null) {
            userPoint.deduct(request.pointAmountToUse());
        }
    }

    // ── 리소스 복원 ──

    private void restoreResources(OrderEntity order) {
        order.getItems().forEach(item ->
                productRepository.findByProductId(item.getProductId())
                        .orElseThrow(() -> OrderException.productNotFound(item.getProductId()))
                        .restoreStock(item.getQuantity())
        );
        restoreCoupon(order);
        restorePoints(order);
    }

    private void restoreCoupon(OrderEntity order) {
        if (order.getCouponId() != null) {
            couponRepository.findByCouponId(order.getCouponId())
                    .ifPresent(CouponEntity::restore);
        }
    }

    private void restorePoints(OrderEntity order) {
        if (order.getPointAmountToUse() != null && order.getPointAmountToUse() > 0) {
            userPointRepository.findByUserId(order.getUserId())
                    .ifPresent(userPoint -> userPoint.restore(order.getPointAmountToUse()));
        }
    }

    // ── 공통 유틸 ──

    private OrderEntity findOrderOrThrow(String orderId) {
        return orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> OrderException.orderNotFound(orderId));
    }

    private OrderResponse toResponse(OrderEntity order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getProductId(), item.getQuantity(),
                        item.getPrice(), item.getItemStatus().name()))
                .toList();

        return new OrderResponse(
                order.getOrderId(), order.getUserId(), items,
                order.getTotalAmount(), order.getCouponDiscountAmount(),
                order.getPointDiscountAmount(), order.getPgPaymentAmount(),
                order.getDeliveryFee(), order.getStatus().name(),
                order.getCouponId(),
                order.getCreatedAt().toString());
    }
}
