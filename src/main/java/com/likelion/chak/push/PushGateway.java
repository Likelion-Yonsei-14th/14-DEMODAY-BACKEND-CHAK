package com.likelion.chak.push;

/** 푸시 서비스(구글·애플·모질라 등)에 암호화된 알림을 보내는 곳. 테스트에서는 가짜로 바꾼다. */
public interface PushGateway {

    /** 푸시 서비스가 돌려준 HTTP 상태 코드. 연결에 실패하면 -1. */
    int send(String endpoint, String vapidAuthorization, byte[] body);
}
