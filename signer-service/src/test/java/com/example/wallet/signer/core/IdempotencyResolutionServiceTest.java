package com.example.wallet.signer.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.wallet.signer.api.IdempotencyResolutionRequest;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class IdempotencyResolutionServiceTest {
    private static final String IDEMPOTENCY_KEY = "withdraw-v1:1:000000";
    private static final String REASON = "KMS response status is unknown";

    private JdbcTemplate jdbc;
    private AuditChainService audit;
    private IdempotencyResolutionService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        audit = mock(AuditChainService.class);
        service = new IdempotencyResolutionService(jdbc, audit);
        actor("CN=wallet-key-admin-proposer");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void proposesResolutionOnlyForProcessingRequest() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers
                .<ResultSetExtractor<String>>any(), eq(IDEMPOTENCY_KEY))).thenReturn("PROCESSING");
        when(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(42L);

        long resolutionId = service.propose(new IdempotencyResolutionRequest(IDEMPOTENCY_KEY, REASON));

        org.assertj.core.api.Assertions.assertThat(resolutionId).isEqualTo(42L);
        verify(audit).append("SIGNING_RESOLUTION_PROPOSED", "CN=wallet-key-admin-proposer", IDEMPOTENCY_KEY,
                Map.of("resolutionId", 42L, "reason", REASON));
    }

    @Test
    void rejectsProposalWhenRequestIsNotProcessing() {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers
                .<ResultSetExtractor<String>>any(), eq(IDEMPOTENCY_KEY))).thenReturn("COMPLETED");

        assertThatThrownBy(() -> service.propose(new IdempotencyResolutionRequest(IDEMPOTENCY_KEY, REASON)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("only a processing signing request can be resolved");

        verify(audit, never()).append(anyString(), anyString(), anyString(), any());
    }

    @Test
    void rejectsUnsafeResolutionRequestBeforeReadingDatabase() {
        assertThatThrownBy(() -> service.propose(new IdempotencyResolutionRequest(
                "invalid key", "   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("valid idempotency key is required");

        verify(jdbc, never()).query(anyString(), org.mockito.ArgumentMatchers
                .<ResultSetExtractor<String>>any(), anyString());
    }

    @Test
    void requiresDifferentApproverBeforeFailingRequest() {
        pendingResolution("CN=wallet-key-admin-proposer");

        assertThatThrownBy(() -> service.approve(42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("proposer and approver must be different identities");

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers.startsWith("UPDATE signer_idempotency SET"),
                any(), any());
    }

    @Test
    void differentApproverCanMarkProcessingRequestAsFailed() {
        pendingResolution("CN=wallet-key-admin-proposer");
        actor("CN=wallet-key-admin-approver");
        when(jdbc.update(org.mockito.ArgumentMatchers.startsWith("UPDATE signer_idempotency SET"), any(), eq(IDEMPOTENCY_KEY)))
                .thenReturn(1);
        when(jdbc.update(org.mockito.ArgumentMatchers.startsWith("UPDATE signer_idempotency_resolution SET"),
                eq("CN=wallet-key-admin-approver"), any(), eq(42L), eq("CN=wallet-key-admin-approver")))
                .thenReturn(1);

        service.approve(42L);

        verify(audit).append("SIGNING_RESOLUTION_APPROVED", "CN=wallet-key-admin-approver", IDEMPOTENCY_KEY,
                Map.of("resolutionId", 42L, "reason", REASON,
                        "proposedBy", "CN=wallet-key-admin-proposer", "result", "FAILED"));
    }

    private void pendingResolution(String proposer) {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers
                .<ResultSetExtractor<IdempotencyResolutionService.Resolution>>any(), anyLong()))
                .thenReturn(new IdempotencyResolutionService.Resolution(
                        IDEMPOTENCY_KEY, REASON, "PENDING", proposer));
    }

    private void actor(String actor) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null));
    }
}
