package com.likelion.chak.service;

import com.likelion.chak.domain.AuthProvider;

/** 인가 코드를 제공자 사용자 정보로 교환한다. 제공자마다 구현이 하나씩 있다. */
public interface OAuthClient {

    AuthProvider provider();

    OAuthProfile authenticate(String code, String redirectUri);
}
