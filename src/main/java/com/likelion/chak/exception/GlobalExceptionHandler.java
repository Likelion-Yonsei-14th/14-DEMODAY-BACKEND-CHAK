package com.likelion.chak.exception;

import com.likelion.chak.alert.AlertMessage;
import com.likelion.chak.alert.AlertSanitizer;
import com.likelion.chak.alert.ErrorAlertService;
import com.likelion.chak.config.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorAlertService alertService;

    @ExceptionHandler(InsufficientChaltteokException.class)
    public ResponseEntity<ChaltteokErrorResponse> handleInsufficient(InsufficientChaltteokException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ChaltteokErrorResponse(
                        errorCode.getCode(), errorCode.getMessage(), e.getRequired(), e.getBalance()));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            // 외부 연동(카카오·구글·토스·S3)이나 설정 문제처럼 운영자가 알아야 하는 서버 쪽 오류
            alertService.alert(AlertMessage.builder(
                            "서버", "서버 오류 " + errorCode.getCode(), "server|" + errorCode.getCode(), AlertMessage.Severity.ERROR)
                    .description(errorCode.getMessage())
                    .field("요청", request.getMethod() + " " + AlertSanitizer.path(request.getRequestURI(), 120))
                    .field("코드", errorCode.getCode())
                    .field("사용자", currentUserId())
                    .build());
        }

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception e) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }

    /** 낙관적 락 충돌, 락 대기 시간 초과, 데드락 패배: 아무것도 반영되지 않았으니 다시 시도하면 된다. */
    @ExceptionHandler({
            ObjectOptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class
    })
    public ResponseEntity<ErrorResponse> handleConcurrency(Exception e) {
        log.warn("Concurrent update conflict: {}", e.toString());
        return respond(ErrorCode.CONCURRENT_REQUEST);
    }

    /** 유니크·CHECK 제약 위반은 코드 버그나 중복 요청이므로 내용은 로그에만 남기고 일반 오류로 응답한다. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException e) {
        log.error("Data integrity violation", e);
        return respond(ErrorCode.CONCURRENT_REQUEST);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        return respond(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        return respond(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception", e);
        String where = request.getMethod() + " " + AlertSanitizer.path(request.getRequestURI(), 120);
        alertService.alert(AlertMessage.builder(
                        "서버", "처리되지 않은 오류", "server|" + e.getClass().getName() + "|" + where, AlertMessage.Severity.ERROR)
                .description(AlertSanitizer.clean(e.getMessage(), 300))
                .field("요청", where)
                .field("예외", e.getClass().getSimpleName())
                .field("위치", firstFrame(e))
                .field("사용자", currentUserId())
                .build());
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }

    /** 로그인한 사용자의 숫자 ID만 알린다(이름·이메일 제외). */
    private static String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthenticatedUser user ? String.valueOf(user.userId()) : null;
    }

    private static String firstFrame(Throwable e) {
        for (StackTraceElement frame : e.getStackTrace()) {
            if (frame.getClassName().startsWith("com.likelion.chak")) {
                return frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1)
                        + "." + frame.getMethodName() + ":" + frame.getLineNumber();
            }
        }
        return null;
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.from(errorCode));
    }
}
