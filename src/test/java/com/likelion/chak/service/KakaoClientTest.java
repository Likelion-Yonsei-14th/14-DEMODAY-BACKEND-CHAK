package com.likelion.chak.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoClientTest {

    @Test
    void profileImageUrlIsStoredAsHttps() {
        assertThat(KakaoClient.toHttps("http://img1.kakaocdn.net/thumb/R640x640.q70/?fname=x.jpg"))
                .isEqualTo("https://img1.kakaocdn.net/thumb/R640x640.q70/?fname=x.jpg");
        assertThat(KakaoClient.toHttps("https://img1.kakaocdn.net/a.jpg")).isEqualTo("https://img1.kakaocdn.net/a.jpg");
        assertThat(KakaoClient.toHttps(null)).isNull();
    }
}
