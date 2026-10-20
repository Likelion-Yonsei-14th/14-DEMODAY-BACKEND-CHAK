package com.likelion.chak.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** 웹훅이 설정되지 않은 환경(로컬·테스트)에서는 로그로만 남긴다. */
@Component
@ConditionalOnExpression("'${chak.alert.discord-webhook-url:}' == ''")
public class LogAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(LogAlertNotifier.class);

    @Override
    public void notify(AlertMessage message, int suppressedSinceLast) {
        log.warn("ALERT [{}] {} {} (추가 발생 {}회)", message.source(), message.title(), message.fields(), suppressedSinceLast);
    }
}
