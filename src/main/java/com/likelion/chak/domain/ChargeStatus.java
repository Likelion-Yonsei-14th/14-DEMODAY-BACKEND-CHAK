package com.likelion.chak.domain;

/** READY(발급) → CONFIRMING(승인 요청 중) → DONE(잔액 반영). 승인 실패 시 CONFIRMING → READY로 되돌린다. */
public enum ChargeStatus {
    READY,
    CONFIRMING,
    DONE
}
