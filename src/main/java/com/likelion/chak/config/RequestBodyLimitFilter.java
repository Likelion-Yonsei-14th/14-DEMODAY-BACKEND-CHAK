package com.likelion.chak.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 요청 본문 크기를 JSON 파싱 전에 자른다. Content-Length가 한도를 넘으면 바로 413을 주고,
 * 길이를 밝히지 않은(chunked) 요청은 읽은 바이트를 세다가 한도를 넘는 순간 읽기를 실패시킨다.
 * 편지 카드(사진 data URL 포함, 서버 검증 한도 50만 자)를 받는 편지 작성 경로만 큰 한도를 쓰고 나머지는 작은 한도를 쓴다.
 */
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    /** 편지 카드를 싣는 경로: POST /api/public/desks/{supporterToken}/messages */
    private static final Pattern LARGE_BODY_PATH = Pattern.compile("^/api/public/desks/[^/]+/messages/?$");

    private final long defaultLimit;
    private final long largeLimit;

    public RequestBodyLimitFilter(long defaultLimit, long largeLimit) {
        this.defaultLimit = defaultLimit;
        this.largeLimit = Math.max(defaultLimit, largeLimit);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = limitFor(request);
        long declared = request.getContentLengthLong();
        if (declared > limit) {
            reject(response);
            return;
        }
        chain.doFilter(new LimitedRequest(request, limit), response);
    }

    long limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return "POST".equalsIgnoreCase(request.getMethod()) && LARGE_BODY_PATH.matcher(path).matches()
                ? largeLimit : defaultLimit;
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Connection", "close");
        response.getWriter().write("{\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"요청 본문이 너무 큽니다.\"}");
    }

    /** 한도를 넘게 읽으려 하면 IOException을 던진다. 파싱 중이던 Jackson은 400으로 실패한다. */
    public static final class PayloadTooLargeException extends IOException {
        PayloadTooLargeException(long limit) {
            super("Request body exceeds " + limit + " bytes");
        }
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long limit;
        private ServletInputStream stream;
        private BufferedReader reader;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream(), limit);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                String encoding = getCharacterEncoding();
                Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
                reader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
            }
            return reader;
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long limit;
        private long count;

        LimitedInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        private void add(long read) throws IOException {
            if (read > 0) {
                count += read;
                if (count > limit) {
                    throw new PayloadTooLargeException(limit);
                }
            }
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) add(1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            add(read);
            return read;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
