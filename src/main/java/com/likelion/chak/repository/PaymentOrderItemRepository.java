package com.likelion.chak.repository;

import com.likelion.chak.domain.PaymentOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentOrderItemRepository extends JpaRepository<PaymentOrderItem, Long> {

    List<PaymentOrderItem> findAllByOrderId(Long orderId);
}
