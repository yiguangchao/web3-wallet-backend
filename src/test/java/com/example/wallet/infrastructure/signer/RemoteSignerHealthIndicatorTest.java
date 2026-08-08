package com.example.wallet.infrastructure.signer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RemoteSignerHealthIndicatorTest {
    private SignerProperties properties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new SignerProperties();
        properties.setRemoteUrl("https://signer.internal/");
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    @Test
    void reportsUpWhenSignerReadinessIsUp() {
        server.expect(once(), requestTo("https://signer.internal/actuator/health/readiness"))
                .andRespond(withSuccess("{\"status\":\"UP\"}", MediaType.APPLICATION_JSON));

        var health = new RemoteSignerHealthIndicator(restClientBuilder, properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("signerStatus", "UP");
        server.verify();
    }

    @Test
    void reportsDownWhenSignerDoesNotReportUp() {
        server.expect(once(), requestTo("https://signer.internal/actuator/health/readiness"))
                .andRespond(withSuccess("{\"status\":\"OUT_OF_SERVICE\"}", MediaType.APPLICATION_JSON));

        var health = new RemoteSignerHealthIndicator(restClientBuilder, properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsOnly(entry("reason", "signer-not-ready"));
        server.verify();
    }

    @Test
    void reportsStableFailureWithoutLeakingRemoteResponse() {
        server.expect(once(), requestTo("https://signer.internal/actuator/health/readiness"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("secret signer failure details")
                        .contentType(MediaType.TEXT_PLAIN));

        var health = new RemoteSignerHealthIndicator(restClientBuilder, properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsOnly(entry("reason", "signer-health-unavailable"));
        server.verify();
    }

    @Test
    void rejectsInvalidHealthConfigurationWithoutCallingRemoteService() {
        properties.setRemoteUrl("http://signer.internal");

        var health = new RemoteSignerHealthIndicator(restClientBuilder, properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsOnly(entry("reason", "signer-health-config-invalid"));
        server.verify();
    }

    @Test
    void rejectsMalformedHealthUrlWithoutThrowing() {
        properties.setRemoteUrl("not a URL");

        var health = new RemoteSignerHealthIndicator(restClientBuilder, properties).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsOnly(entry("reason", "signer-health-config-invalid"));
        server.verify();
    }
}
