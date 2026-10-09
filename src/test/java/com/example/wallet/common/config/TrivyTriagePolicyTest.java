package com.example.wallet.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class TrivyTriagePolicyTest {
    @Test
    @SuppressWarnings("unchecked")
    void shouldLimitExceptionsToReviewedCvesAndExactVersionWithExpiry() throws Exception {
        Map<String, Object> policy = new Yaml().load(Files.readString(Path.of(".trivyignore.yaml")));
        assertThat(policy.keySet()).containsExactly("vulnerabilities");
        List<Map<String, Object>> exceptions = (List<Map<String, Object>>) policy.get("vulnerabilities");
        assertThat(exceptions).extracting(item -> item.get("id"))
                .containsExactlyInAnyOrder("CVE-2026-47884", "CVE-2026-47890");
        for (var exception : exceptions) {
            assertThat((List<String>) exception.get("purls"))
                    .containsExactly("pkg:maven/org.springframework/spring-webmvc@6.2.19");
            assertThat(exception.get("expired_at")).isNotNull();
            assertThat(exception.get("statement").toString()).contains("RestOnlyMvcGuard");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepRawSarifAndBlockingSeverityGate() throws Exception {
        Map<String, Object> workflow = new Yaml().load(Files.readString(Path.of(".github/workflows/ci.yml")));
        var jobs = (Map<String, Map<String, Object>>) workflow.get("jobs");
        var steps = (List<Map<String, Object>>) jobs.get("container-security").get("steps");
        for (var step : steps) {
            if ("Generate Trivy security report".equals(step.get("name"))) {
                assertThat((Map<String, Object>) step.get("with")).doesNotContainKey("trivyignores");
            }
            if ("Enforce image vulnerability gate".equals(step.get("name"))) {
                var options = (Map<String, Object>) step.get("with");
                assertThat(options).containsEntry("severity", "HIGH,CRITICAL")
                        .containsEntry("exit-code", 1).containsEntry("trivyignores", ".trivyignore.yaml");
            }
        }
    }
}
