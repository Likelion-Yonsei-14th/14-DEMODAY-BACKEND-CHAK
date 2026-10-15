package com.likelion.chak.exception;

import lombok.Getter;

/** 잔액 부족. 응답 본문에 필요한 찰떡(required)과 현재 잔액(balance)을 함께 내려준다. */
@Getter
public class InsufficientChaltteokException extends CustomException {

    private final long required;
    private final long balance;

    public InsufficientChaltteokException(long required, long balance) {
        super(ErrorCode.INSUFFICIENT_CHALTTEOK);
        this.required = required;
        this.balance = balance;
    }
}
