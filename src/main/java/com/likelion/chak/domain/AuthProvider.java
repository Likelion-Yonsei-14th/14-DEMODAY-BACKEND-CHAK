package com.likelion.chak.domain;

/** 인증 제공자. 이름은 DB(users.provider)에 그대로 저장되므로 바꾸지 않는다. */
public enum AuthProvider {
    KAKAO,
    GOOGLE,
    TEST
}
