package com.likelion.chak.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * 가장 앞단 서블릿 필터들의 순서를 직접 정한다.
 * <ol>
 *   <li>본문 크기 제한(파싱 전에 큰 요청을 끊는다)</li>
 *   <li>접속자 IP 계산(원본 X-Forwarded-For가 필요하다)</li>
 *   <li>ForwardedHeaderFilter(X-Forwarded-Proto/Host 반영). 예전 {@code server.forward-headers-strategy=framework}와 같은
 *       필터지만, 접속자 IP 계산보다 뒤에 오도록 여기서 등록한다(그래서 그 설정은 none으로 둔다).</li>
 * </ol>
 */
@Configuration
public class WebFilterConfig {

    @Bean
    public FilterRegistrationBean<RequestBodyLimitFilter> requestBodyLimitFilter(
            @Value("${chak.http.max-body-bytes:524288}") long defaultLimit,
            @Value("${chak.http.max-message-body-bytes:2097152}") long messageLimit) {
        FilterRegistrationBean<RequestBodyLimitFilter> registration =
                new FilterRegistrationBean<>(new RequestBodyLimitFilter(defaultLimit, messageLimit));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ClientIpFilter> clientIpFilter(
            @Value("${chak.client-ip.trusted-hops:2}") int trustedHops,
            @Value("${chak.client-ip.edge-secret:}") String edgeSecret) {
        FilterRegistrationBean<ClientIpFilter> registration =
                new FilterRegistrationBean<>(new ClientIpFilter(trustedHops, edgeSecret));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> chakForwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> registration =
                new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }
}
