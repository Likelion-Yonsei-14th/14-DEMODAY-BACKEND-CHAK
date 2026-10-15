package com.likelion.chak.service;

/** 충전 결제 승인 수단. 토스페이먼츠 구현과 개발용 mock이 있고, 설정이 없으면 충전은 거부된다. */
public interface ChargeGateway {

    /**
     * 결제를 승인한다. 승인되지 않았거나 금액이 다르면 예외를 던진다.
     * 같은 orderId로 다시 호출해도 이중 승인되지 않아야 한다.
     */
    void confirm(String paymentKey, String orderId, long amount);
}
