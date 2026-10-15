package com.likelion.chak.domain;

/** 소셜 로그인 제공자. 이름은 DB(users.provider)에 그대로 저장되므로 바꾸지 않는다. */
public enum AuthProvider {
    KAKAO,
    GOOGLE
}
