package com.likelion.chak.exception;

public record ChaltteokErrorResponse(String code, String message, long required, long balance) {
}
