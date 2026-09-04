package com.example.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class DeploymentConfigurationTest {

    @Test
    void shouldDefineCompleteHealthyComposeStack() throws Exception {
        Map<String, Object> compose = new Yaml().load(Files.readString(Path.of("docker-compose.yml")));
        Map<String, Object> services = map(compose.get("services"));

        assertThat(services).containsKeys("app", "mysql", "redis", "anvil");
        assertThat(map(services.get("app"))).containsKeys("build", "depends_on", "healthcheck");
        assertThat(map(services.get("mysql"))).containsKey("healthcheck");
        assertThat(map(services.get("redis"))).containsKey("healthcheck");
        assertThat(map(services.get("anvil"))).containsKey("healthcheck");
    }

    @Test
    void shouldRunImageAsNonRootWithReadinessProbe() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertThat(dockerfile).contains("USER wallet");
        assertThat(dockerfile).contains("/actuator/health/readiness");
        assertThat(dockerfile).doesNotContain("USER root");
    }

    @Test
    void shouldKeepProductionSecretsExternalAndDisableSwaggerByDefault() throws Exception {
        String production = Files.readString(Path.of("src/main/resources/application-prod.yml"));

        assertThat(production).contains("remote-url: ${WALLET_SIGNER_REMOTE_URL}");
        assertThat(production).contains(
                "include: readinessState,db,redis,remoteSigner,rpcQuorum");
        assertThat(production).contains(
                "rpc-quorum-max-head-lag: ${WEB3_RPC_QUORUM_MAX_HEAD_LAG:2}");
        assertThat(production).contains(
                "rpc-quorum-preflight-fixed-delay: ${WEB3_RPC_QUORUM_PREFLIGHT_FIXED_DELAY:30000}");
        assertThat(production).contains("secret: ${JWT_SECRET}");
        assertThat(production).contains("deposit-enabled: ${WALLET_DEPOSIT_ENABLED:false}");
        assertThat(production).contains("withdraw-enabled: ${WALLET_WITHDRAW_ENABLED:false}");
        assertThat(production).contains("enabled: ${SWAGGER_ENABLED:false}");
        assertThat(production).doesNotContain("local-private-key");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
