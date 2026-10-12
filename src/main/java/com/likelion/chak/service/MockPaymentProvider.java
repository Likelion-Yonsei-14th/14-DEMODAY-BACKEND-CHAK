package com.likelion.chak.service;

import com.likelion.chak.domain.PaymentOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 실제로 돈이 오가지 않는 개발·시연용 결제. chak.payment.provider=mock 일 때만 켜진다. */
@Component
@ConditionalOnProperty(name = "chak.payment.provider", havingValue = "mock")
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public String approve(PaymentOrder order) {
        return "mock-" + UUID.randomUUID();
    }
}
