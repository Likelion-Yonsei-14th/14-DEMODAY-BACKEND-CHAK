package com.likelion.chak.alert;

/** 알림을 실제로 전달하는 수단(디스코드 등). 예외를 던지지 않아야 하며, 던져도 호출자는 영향을 받지 않는다. */
public interface AlertNotifier {

    void notify(AlertMessage message, int suppressedSinceLast);
}
