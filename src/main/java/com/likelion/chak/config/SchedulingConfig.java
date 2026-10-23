package com.likelion.chak.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 예약 작업(DB 비밀번호 확인, 오래된 기록 정리)을 켠다. 개별 작업의 활성화 여부와 무관하게 한 곳에서 켠다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
