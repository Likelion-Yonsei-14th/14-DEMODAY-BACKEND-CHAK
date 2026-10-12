package com.likelion.chak.service;

import com.likelion.chak.domain.OrderStatus;
import com.likelion.chak.domain.PaymentOrder;
import com.likelion.chak.domain.PaymentOrderItem;
import com.likelion.chak.domain.ProductCode;
import com.likelion.chak.dto.PaymentDtos.OrderCreateRequest;
import com.likelion.chak.dto.PaymentDtos.OrderItemRequest;
import com.likelion.chak.dto.PaymentDtos.OrderItemResponse;
import com.likelion.chak.dto.PaymentDtos.OrderResponse;
import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.repository.PaymentOrderItemRepository;
import com.likelion.chak.repository.PaymentOrderRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 주문 생성·승인과, 결제한 항목을 편지 전송 등에 한 번만 사용하도록 소비하는 규칙. */
@Service
public class PaymentService {

    private final PaymentOrderRepository orderRepository;
    private final PaymentOrderItemRepository itemRepository;
    private final PaymentProvider provider;

    public PaymentService(
            PaymentOrderRepository orderRepository,
            PaymentOrderItemRepository itemRepository,
            ObjectProvider<PaymentProvider> providerHolder) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.provider = providerHolder.getIfAvailable();
    }

    @Transactional
    public OrderResponse createOrder(CallerIdentity caller, OrderCreateRequest request) {
        if (!caller.isPresent()) {
            throw new CustomException(ErrorCode.IDENTITY_REQUIRED);
        }
        Map<String, Integer> merged = new java.util.LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            String key = item.code().name() + "|" + (item.ref() == null ? "" : item.ref().trim());
            merged.merge(key, item.quantity() == null ? 1 : item.quantity(), Integer::sum);
        }
        int total = 0;
        for (Map.Entry<String, Integer> entry : merged.entrySet()) {
            if (entry.getValue() > 100) {
                throw new CustomException(ErrorCode.INVALID_ORDER);
            }
            total += ProductCode.valueOf(entry.getKey().split("\\|", -1)[0]).unitPrice() * entry.getValue();
        }

        PaymentOrder order = orderRepository.save(PaymentOrder.create(
                caller.user(), caller.guest(), total, provider == null ? "NONE" : provider.name()));
        merged.forEach((key, quantity) -> {
            String[] parts = key.split("\\|", -1);
            itemRepository.save(PaymentOrderItem.create(
                    order, ProductCode.valueOf(parts[0]), parts[1].isEmpty() ? null : parts[1], quantity));
        });
        return toResponse(order);
    }

    @Transactional
    public OrderResponse confirm(CallerIdentity caller, Long orderId) {
        PaymentOrder order = findOwned(caller, orderId, true);
        if (order.getStatus() == OrderStatus.PAID) {
            return toResponse(order);
        }
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new CustomException(ErrorCode.INVALID_ORDER);
        }
        if (provider == null) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
        order.markPaid(provider.approve(order), Instant.now());
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(CallerIdentity caller, Long orderId) {
        return toResponse(findOwned(caller, orderId, false));
    }

    /**
     * 결제한 주문이 필요한 항목을 모두 포함하는지 확인하고 사용 처리한다. 주문은 한 번만 쓸 수 있다.
     * 필요한 항목이 없으면 아무것도 하지 않는다.
     */
    @Transactional
    public void consume(CallerIdentity caller, Long orderId, Map<ProductCode, Integer> required) {
        if (required.isEmpty()) {
            return;
        }
        if (orderId == null) {
            throw new CustomException(ErrorCode.PAYMENT_REQUIRED);
        }
        PaymentOrder order = findOwned(caller, orderId, true);
        if (order.getStatus() != OrderStatus.PAID) {
            throw new CustomException(ErrorCode.PAYMENT_REQUIRED);
        }
        if (order.isConsumed()) {
            throw new CustomException(ErrorCode.ORDER_ALREADY_USED);
        }
        Map<ProductCode, Integer> paid = new EnumMap<>(ProductCode.class);
        itemRepository.findAllByOrderId(order.getId()).forEach(i -> paid.merge(i.getCode(), i.getQuantity(), Integer::sum));
        boolean covered = required.entrySet().stream()
                .allMatch(e -> paid.getOrDefault(e.getKey(), 0) >= e.getValue());
        if (!covered) {
            throw new CustomException(ErrorCode.PAYMENT_REQUIRED);
        }
        order.markConsumed(Instant.now());
    }

    private PaymentOrder findOwned(CallerIdentity caller, Long orderId, boolean lock) {
        PaymentOrder order = (lock ? orderRepository.findForUpdateById(orderId) : orderRepository.findById(orderId))
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.belongsTo(caller.user(), caller.guest())) {
            throw new CustomException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private OrderResponse toResponse(PaymentOrder order) {
        List<OrderItemResponse> items = itemRepository.findAllByOrderId(order.getId()).stream()
                .map(i -> new OrderItemResponse(i.getCode(), i.getRef(), i.getQuantity(), i.getUnitPrice(), i.getAmount()))
                .toList();
        return new OrderResponse(order.getId(), order.getStatus(), order.getTotalAmount(),
                order.isConsumed(), items, order.getCreatedAt(), order.getPaidAt());
    }
}
