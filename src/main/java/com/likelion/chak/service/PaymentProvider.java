package com.likelion.chak.service;

import com.likelion.chak.domain.PaymentOrder;

/**
 * 결제 승인 수단. 실제 PG(토스페이먼츠 등)를 붙일 때 이 인터페이스를 구현해 교체한다.
 * 현재 구현은 개발용 mock 하나뿐이며, 설정이 없으면 결제는 거부된다.
 */
public interface PaymentProvider {

    String name();

    /** 주문을 승인하고 PG 거래 식별자를 반환한다. 승인할 수 없으면 예외를 던진다. */
    String approve(PaymentOrder order);
}
