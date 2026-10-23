package com.likelion.chak.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecretsManagerEnvironmentPostProcessorTest {

    private final SecretsManagerEnvironmentPostProcessor processor = new SecretsManagerEnvironmentPostProcessor(
            new DeferredLogFactory() {
                @Override
                public org.apache.commons.logging.Log getLog(java.util.function.Supplier<org.apache.commons.logging.Log> destination) {
                    return mock(org.apache.commons.logging.Log.class);
                }

                @Override
                public org.apache.commons.logging.Log getLog(Class<?> destination) {
                    return mock(org.apache.commons.logging.Log.class);
                }

                @Override
                public org.apache.commons.logging.Log getLog(org.apache.commons.logging.Log destination) {
                    return mock(org.apache.commons.logging.Log.class);
                }
            });

    @Test
    void addsSecretKeysAsLowestPrecedenceProperties() {
        MockEnvironment env = new MockEnvironment().withProperty("EXISTING", "from-env");
        processor.apply(env, "chak/prod/app", id -> """
                {"DISCORD_WEBHOOK_URL":"https://discord.com/api/webhooks/1/x","EXISTING":"from-secret",
                 "EMPTY":"", "NUMBER": 5}""");

        assertThat(env.getProperty("DISCORD_WEBHOOK_URL")).isEqualTo("https://discord.com/api/webhooks/1/x");
        assertThat(env.getProperty("EXISTING")).isEqualTo("from-env");
        assertThat(env.getProperty("EMPTY")).isNull();
        assertThat(env.getProperty("NUMBER")).isNull();
        assertThat(env.getPropertySources().get(SecretsManagerEnvironmentPostProcessor.SOURCE_NAME)).isNotNull();
    }

    @Test
    void secretValuesResolveInsidePlaceholders() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("chak.alert.discord-webhook-url", "${DISCORD_WEBHOOK_URL:}");
        processor.apply(env, "chak/prod/app", id -> "{\"DISCORD_WEBHOOK_URL\":\"https://discord.com/api/webhooks/9/z\"}");

        assertThat(env.getProperty("chak.alert.discord-webhook-url")).isEqualTo("https://discord.com/api/webhooks/9/z");
    }

    @Test
    void failuresNeverStopStartup() {
        MockEnvironment env = new MockEnvironment();
        processor.apply(env, "chak/prod/app", id -> {
            throw new IllegalStateException("AccessDenied");
        });
        processor.apply(env, "chak/prod/app", id -> "not json");

        assertThat(env.getPropertySources().contains(SecretsManagerEnvironmentPostProcessor.SOURCE_NAME)).isFalse();
    }

    @Test
    void doesNothingWithoutASecretId() {
        MockEnvironment env = new MockEnvironment();
        processor.postProcessEnvironment(env, null);

        assertThat(env.getPropertySources().contains(SecretsManagerEnvironmentPostProcessor.SOURCE_NAME)).isFalse();
    }

    @Test
    void parseKeepsOnlyNonBlankStrings() {
        Map<String, Object> parsed = SecretsManagerEnvironmentPostProcessor.parse("{\"A\":\"x\",\"B\":\" \",\"C\":null,\"D\":true}");
        assertThat(parsed).containsOnlyKeys("A");
        assertThat(new MapPropertySource("x", parsed).getProperty("A")).isEqualTo("x");
    }

    @Test
    void isRegisteredWithSpringBootSoItRunsAtStartup() throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream("META-INF/spring.factories")) {
            String factories = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(factories).contains(
                    "org.springframework.boot.EnvironmentPostProcessor=" + SecretsManagerEnvironmentPostProcessor.class.getName());
        }
    }

    @Test
    void dbCredentialsFromTheRdsSecretOverrideTheStaleEnvironmentValues() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.username", "admin")
                .withProperty("spring.datasource.password", "old-password");
        processor.applyDbCredentials(env, "rds!db-x", id -> "{\"username\":\"admin\",\"password\":\"rotated-password\"}");

        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("rotated-password");
        assertThat(env.getPropertySources().get(SecretsManagerEnvironmentPostProcessor.DB_SOURCE_NAME)).isNotNull();
    }

    @Test
    void dbCredentialsKeepTheEnvironmentWhenTheSecretCannotBeRead() {
        MockEnvironment env = new MockEnvironment().withProperty("spring.datasource.password", "from-env");
        processor.applyDbCredentials(env, "rds!db-x", id -> {
            throw new IllegalStateException("AccessDenied");
        });
        processor.applyDbCredentials(env, "rds!db-x", id -> "{\"username\":\"admin\"}");
        processor.applyDbCredentials(env, "", id -> {
            throw new AssertionError("must not fetch when no secret id is configured");
        });

        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("from-env");
    }
}
