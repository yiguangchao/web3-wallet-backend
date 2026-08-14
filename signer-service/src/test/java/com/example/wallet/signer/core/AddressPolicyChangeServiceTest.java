package com.example.wallet.signer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.wallet.signer.api.AddressPolicyChangeRequest;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AddressPolicyChangeServiceTest {
    private static final String KEY_ID = "withdraw-v1";
    private static final long CHAIN_ID = 11155111L;
    private static final String ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final String REASON = "Recipient passed compliance review";

    private JdbcTemplate jdbc;
    private AuditChainService audit;
    private AddressPolicyChangeService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        audit = mock(AuditChainService.class);
        service = new AddressPolicyChangeService(jdbc, audit);
        actor("CN=wallet-key-admin-proposer");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void proposesAddressAdditionForActiveKey() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(KEY_ID))).thenReturn(1);
        when(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(42L);

        long changeId = service.propose(new AddressPolicyChangeRequest(
                KEY_ID, CHAIN_ID, ADDRESS, "ADD", REASON));

        assertThat(changeId).isEqualTo(42L);
        verify(audit).append("ADDRESS_POLICY_CHANGE_PROPOSED", "CN=wallet-key-admin-proposer", "42",
                Map.of("keyId", KEY_ID, "chainId", CHAIN_ID, "toAddress", ADDRESS,
                        "action", "ADD", "reason", REASON));
    }

    @Test
    void rejectsProposalForInactiveKey() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(KEY_ID))).thenReturn(0);

        assertThatThrownBy(() -> service.propose(new AddressPolicyChangeRequest(
                KEY_ID, CHAIN_ID, ADDRESS, "ADD", REASON)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("active signing key not found");

        verify(audit, never()).append(anyString(), anyString(), anyString(), any());
    }

    @Test
    void requiresDifferentApprover() {
        pendingChange("CN=wallet-key-admin-proposer", "ADD");

        assertThatThrownBy(() -> service.approve(42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("proposer and approver must be different identities");

        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void differentApproverCanAddAllowlistedAddress() {
        pendingChange("CN=wallet-key-admin-proposer", "ADD");
        actor("CN=wallet-key-admin-approver");
        when(jdbc.update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO signer_address_policy"),
                eq(KEY_ID), eq(CHAIN_ID), eq(ADDRESS), any(), eq(KEY_ID))).thenReturn(1);
        when(jdbc.update(org.mockito.ArgumentMatchers.startsWith("UPDATE signer_address_policy_change SET"),
                eq("CN=wallet-key-admin-approver"), any(), eq(42L), eq("CN=wallet-key-admin-approver")))
                .thenReturn(1);

        service.approve(42L);

        verify(audit).append("ADDRESS_POLICY_CHANGE_APPROVED", "CN=wallet-key-admin-approver", "42",
                Map.of("keyId", KEY_ID, "chainId", CHAIN_ID, "toAddress", ADDRESS, "action", "ADD",
                        "reason", REASON, "proposedBy", "CN=wallet-key-admin-proposer"));
    }

    @Test
    void rejectsInvalidRequestBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.propose(new AddressPolicyChangeRequest(
                "", CHAIN_ID, ADDRESS, "ADD", REASON)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("address policy change request is invalid");

        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), anyString());
    }

    private void pendingChange(String proposer, String action) {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers
                .<ResultSetExtractor<AddressPolicyChangeService.Change>>any(), anyLong()))
                .thenReturn(new AddressPolicyChangeService.Change(
                        KEY_ID, CHAIN_ID, ADDRESS, action, REASON, "PENDING", proposer));
    }

    private void actor(String actor) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null));
    }
}
