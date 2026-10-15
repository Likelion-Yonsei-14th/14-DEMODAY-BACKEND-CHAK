package com.likelion.chak.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 실제로 돈이 오가지 않는 개발·시연용 충전. chak.chaltteok.provider=mock 일 때만 켜진다. */
@Component
@ConditionalOnProperty(name = "chak.chaltteok.provider", havingValue = "mock")
public class MockChargeGateway implements ChargeGateway {

    @Override
    public void confirm(String paymentKey, String orderId, long amount) {
        // 항상 승인
    }
}
