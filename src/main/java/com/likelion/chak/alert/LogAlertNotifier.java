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
        log.warn("ALERT [{}] {} {} (추가 발생 {}회)", oneLine(message.source()), oneLine(message.title()),
                oneLine(String.valueOf(message.fields())), suppressedSinceLast);
    }

    /** 줄바꿈을 보이는 글자로 바꿔, 사용자 입력이 가짜 로그 줄을 만들지 못하게 한다(로그 위조 방지). */
    static String oneLine(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n")
                .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }
}
