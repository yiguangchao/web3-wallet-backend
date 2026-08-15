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

import com.example.wallet.signer.api.TokenPolicyChangeRequest;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class TokenPolicyChangeServiceTest {
    private static final String KEY_ID = "withdraw-v1";
    private static final long CHAIN_ID = 11155111L;
    private static final String TOKEN = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final BigInteger SINGLE_LIMIT = new BigInteger("1000000000");
    private static final BigInteger DAILY_LIMIT = new BigInteger("5000000000");
    private static final String REASON = "USDC policy approved for testnet withdrawals";

    private JdbcTemplate jdbc;
    private AuditChainService audit;
    private TokenPolicyChangeService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        audit = mock(AuditChainService.class);
        service = new TokenPolicyChangeService(jdbc, audit);
        actor("CN=wallet-key-admin-proposer");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void proposesTokenPolicyForActiveKey() {
        activeKey();
        when(jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(42L);

        long changeId = service.propose(request("ADD", SINGLE_LIMIT, DAILY_LIMIT));

        assertThat(changeId).isEqualTo(42L);
        verify(audit).append("TOKEN_POLICY_CHANGE_PROPOSED", "CN=wallet-key-admin-proposer", "42",
                detail("ADD", SINGLE_LIMIT, DAILY_LIMIT));
    }

    @Test
    void rejectsLimitsWhenDailyLimitIsLowerThanSingleLimit() {
        assertThatThrownBy(() -> service.propose(request(
                "UPDATE_LIMITS", DAILY_LIMIT, SINGLE_LIMIT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("token signing limits are invalid");

        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class), anyString(), anyLong());
        verify(audit, never()).append(anyString(), anyString(), anyString(), any());
    }

    @Test
    void rejectsDisableRequestThatContainsLimits() {
        assertThatThrownBy(() -> service.propose(request("DISABLE", SINGLE_LIMIT, DAILY_LIMIT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("disable token policy change must not contain limits");
    }

    @Test
    void requiresDifferentApprover() {
        pendingChange("CN=wallet-key-admin-proposer", "ADD", SINGLE_LIMIT, DAILY_LIMIT);

        assertThatThrownBy(() -> service.approve(42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("proposer and approver must be different identities");

        verify(jdbc, never()).update(org.mockito.ArgumentMatchers
                .startsWith("INSERT INTO signer_token_policy"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void differentApproverCanAddTokenPolicy() {
        pendingChange("CN=wallet-key-admin-proposer", "ADD", SINGLE_LIMIT, DAILY_LIMIT);
        actor("CN=wallet-key-admin-approver");
        activeKey();
        when(jdbc.update(org.mockito.ArgumentMatchers
                        .startsWith("UPDATE signer_token_policy_change"),
                eq("CN=wallet-key-admin-approver"), any(), eq(42L),
                eq("CN=wallet-key-admin-approver"))).thenReturn(1);

        service.approve(42L);

        verify(jdbc).update(org.mockito.ArgumentMatchers
                        .startsWith("INSERT INTO signer_token_policy"),
                eq(KEY_ID), eq(CHAIN_ID), eq(TOKEN), eq(SINGLE_LIMIT), eq(DAILY_LIMIT), any());
        Map<String, Object> auditDetail = detail("ADD", SINGLE_LIMIT, DAILY_LIMIT);
        auditDetail.put("proposedBy", "CN=wallet-key-admin-proposer");
        verify(audit).append("TOKEN_POLICY_CHANGE_APPROVED", "CN=wallet-key-admin-approver",
                "42", auditDetail);
    }

    @Test
    void updateLimitsRequiresExistingActivePolicy() {
        pendingChange("CN=wallet-key-admin-proposer", "UPDATE_LIMITS", SINGLE_LIMIT, DAILY_LIMIT);
        actor("CN=wallet-key-admin-approver");
        activeKey();
        when(jdbc.update(org.mockito.ArgumentMatchers
                        .startsWith("UPDATE signer_token_policy\nSET single_raw_limit"),
                eq(SINGLE_LIMIT), eq(DAILY_LIMIT), eq(KEY_ID), eq(CHAIN_ID), eq(TOKEN)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.approve(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("active token policy was not found");

        verify(audit, never()).append(eq("TOKEN_POLICY_CHANGE_APPROVED"), anyString(),
                anyString(), any());
    }

    @Test
    void differentApproverCanDisableActivePolicy() {
        pendingChange("CN=wallet-key-admin-proposer", "DISABLE", null, null);
        actor("CN=wallet-key-admin-approver");
        activeKey();
        when(jdbc.update(org.mockito.ArgumentMatchers
                        .startsWith("UPDATE signer_token_policy SET status=0"),
                eq(KEY_ID), eq(CHAIN_ID), eq(TOKEN))).thenReturn(1);
        when(jdbc.update(org.mockito.ArgumentMatchers
                        .startsWith("UPDATE signer_token_policy_change"),
                eq("CN=wallet-key-admin-approver"), any(), eq(42L),
                eq("CN=wallet-key-admin-approver"))).thenReturn(1);

        service.approve(42L);

        Map<String, Object> auditDetail = detail("DISABLE", null, null);
        auditDetail.put("proposedBy", "CN=wallet-key-admin-proposer");
        verify(audit).append("TOKEN_POLICY_CHANGE_APPROVED", "CN=wallet-key-admin-approver",
                "42", auditDetail);
    }

    private TokenPolicyChangeRequest request(String action, BigInteger single, BigInteger daily) {
        return new TokenPolicyChangeRequest(KEY_ID, CHAIN_ID, TOKEN, action, single, daily, REASON);
    }

    private void activeKey() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(KEY_ID), eq(CHAIN_ID)))
                .thenReturn(1);
    }

    private void pendingChange(String proposer, String action, BigInteger single, BigInteger daily) {
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers
                .<ResultSetExtractor<TokenPolicyChangeService.Change>>any(), anyLong()))
                .thenReturn(new TokenPolicyChangeService.Change(
                        KEY_ID, CHAIN_ID, TOKEN, action, single, daily, REASON, "PENDING", proposer));
    }

    private Map<String, Object> detail(String action, BigInteger single, BigInteger daily) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("keyId", KEY_ID);
        detail.put("chainId", CHAIN_ID);
        detail.put("tokenAddress", TOKEN);
        detail.put("action", action);
        if (single != null) {
            detail.put("singleRawLimit", single);
            detail.put("dailyRawLimit", daily);
        }
        detail.put("reason", REASON);
        return detail;
    }

    private void actor(String actor) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null));
    }
}
