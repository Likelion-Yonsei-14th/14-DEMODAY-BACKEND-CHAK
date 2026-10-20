package com.likelion.chak.alert;

import com.likelion.chak.exception.CustomException;
import com.likelion.chak.exception.ErrorCode;
import com.likelion.chak.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GlobalExceptionAlertTest {

    private final ErrorAlertService alerts = mock(ErrorAlertService.class);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(alerts);

    @Test
    void unhandledExceptionAlertsWithRequestAndWithoutLeakingValues() {
        var request = new MockHttpServletRequest("POST", "/api/public/desks/abc/messages");
        request.setQueryString("token=secret");

        var response = handler.handleException(new IllegalStateException("db password is hunter2 for a@b.com"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        var captor = forClass(AlertMessage.class);
        verify(alerts).alert(captor.capture());
        AlertMessage sent = captor.getValue();
        assertThat(sent.fields().get("요청")).isEqualTo("POST /api/public/desks/abc/messages");
        assertThat(sent.fields().get("예외")).isEqualTo("IllegalStateException");
        assertThat(sent.description()).doesNotContain("a@b.com");
    }

    @Test
    void serverSideBusinessErrorsAlertButClientErrorsDoNot() {
        var request = new MockHttpServletRequest("POST", "/api/auth/google");

        handler.handleCustomException(new CustomException(ErrorCode.GOOGLE_API_ERROR), request);
        verify(alerts).alert(org.mockito.ArgumentMatchers.any());

        org.mockito.Mockito.reset(alerts);
        handler.handleCustomException(new CustomException(ErrorCode.INVALID_REQUEST), request);
        verify(alerts, never()).alert(org.mockito.ArgumentMatchers.any());
    }
}
