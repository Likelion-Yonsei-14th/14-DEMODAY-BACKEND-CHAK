package com.likelion.chak.repository;

import com.likelion.chak.domain.PaymentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentOrder> findForUpdateById(Long id);
}
