package com.likelion.chak.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 시작할 때 AWS Secrets Manager의 앱 시크릿(JSON)을 읽어 설정 값으로 추가한다.
 * 서버의 실행 스크립트를 고치지 않아도, 시크릿에 키를 넣기만 하면(예: DISCORD_WEBHOOK_URL) 앱이 읽는다.
 *
 * <ul>
 *   <li>{@code chak.secrets.id}가 비어 있으면(로컬·테스트) 아무것도 하지 않는다.</li>
 *   <li>환경변수·명령행 등 기존 설정이 항상 우선하고, 시크릿 값은 가장 낮은 우선순위로만 들어간다.</li>
 *   <li>읽지 못해도(권한·네트워크) 기동은 계속하며 원인만 로그에 남긴다. 값은 로그에 남기지 않는다.</li>
 * </ul>
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class SecretsManagerEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "chak-secrets-manager";
    static final String DB_SOURCE_NAME = "chak-db-credentials";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Log log;

    public SecretsManagerEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(SecretsManagerEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String secretId = environment.getProperty("chak.secrets.id", "");
        if (secretId.isBlank()) {
            return;
        }
        String region = firstNonBlank(
                environment.getProperty("AWS_REGION"), environment.getProperty("storage.s3.region"), "ap-southeast-2");
        apply(environment, secretId, id -> fetch(id, region));
        applyDbCredentials(environment, environment.getProperty("chak.db-secret-id", ""), id -> fetch(id, region));
    }

    /**
     * DB 계정 정보를 RDS 관리 시크릿에서 직접 읽어 환경변수보다 우선하게 한다.
     * 이 시크릿은 7일마다 자동으로 비밀번호가 바뀌는데, 서버 실행 스크립트가 시작할 때 한 번만 넘겨 준 비밀번호는
     * 교체 뒤 낡은 값이 되어 DB 연결이 전부 실패한다(2026-10-22 장애). 시작할 때마다 최신 값을 읽으면 재시작만으로 복구된다.
     * 읽지 못하면 기존 환경변수 값으로 계속한다.
     */
    void applyDbCredentials(ConfigurableEnvironment environment, String dbSecretId, Function<String, String> fetcher) {
        if (dbSecretId == null || dbSecretId.isBlank()) {
            return;
        }
        try {
            DbCredentials credentials = DbCredentials.parse(fetcher.apply(dbSecretId));
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("spring.datasource.username", credentials.username());
            values.put("spring.datasource.password", credentials.password());
            environment.getPropertySources().addFirst(new MapPropertySource(DB_SOURCE_NAME, values));
            log.info("Using DB credentials from Secrets Manager (values are never logged)");
        } catch (RuntimeException e) {
            log.warn("Could not load DB credentials from Secrets Manager, keeping the environment values: "
                    + e.getClass().getSimpleName());
        }
    }

    /** 시크릿을 가져오는 방법을 바꿔 끼울 수 있게 분리(테스트용). */
    void apply(ConfigurableEnvironment environment, String secretId, Function<String, String> fetcher) {
        try {
            String json = fetcher.apply(secretId);
            Map<String, Object> values = parse(json);
            if (!values.isEmpty()) {
                environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, values));
            }
            log.info("Loaded " + values.size() + " keys from Secrets Manager (names only, values are never logged)");
        } catch (RuntimeException e) {
            // 알림 설정 같은 선택 값을 못 읽었다고 서비스가 뜨지 못하면 안 된다.
            log.warn("Could not load Secrets Manager config: " + e.getClass().getSimpleName());
        }
    }

    static Map<String, Object> parse(String json) {
        Map<String, Object> values = new LinkedHashMap<>();
        JsonNode root = JSON.readTree(json);
        root.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            if (value.isString() && !value.asString().isBlank()) {
                values.put(entry.getKey(), value.asString());
            }
        });
        return values;
    }

    static String fetch(String secretId, String region) {
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(3))
                        .socketTimeout(Duration.ofSeconds(5)))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(8))
                        .build())
                .build()) {
            return client.getSecretValue(request -> request.secretId(secretId)).secretString();
        }
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }
}
