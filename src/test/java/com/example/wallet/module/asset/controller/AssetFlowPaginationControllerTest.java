package com.example.wallet.module.asset.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.wallet.common.exception.GlobalExceptionHandler;
import com.example.wallet.infrastructure.security.LoginUser;
import com.example.wallet.module.asset.dto.AssetFlowPage;
import com.example.wallet.module.asset.entity.AssetAccount;
import com.example.wallet.module.asset.entity.AssetFlow;
import com.example.wallet.module.asset.entity.AssetFlowBusinessType;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.asset.service.AssetService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AssetController.class)
@ContextConfiguration(classes = AssetController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AssetFlowPaginationControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AssetService service;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(7L, "alice", "USER"), null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReadSingleFlowWithoutCachingAndIgnoreRequestedOwner() throws Exception {
        AssetFlow flow = new AssetFlow();
        flow.setId(99L);
        flow.setBusinessType("DEPOSIT");
        flow.setAmount(new java.math.BigDecimal("1.25"));
        flow.setTxHash("0xhash");
        when(service.getFlow(7L, 99L)).thenReturn(flow);
        mockMvc.perform(get("/api/asset/flows/99").param("userId", "999"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.businessType").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.amount").value(1.25))
                .andExpect(jsonPath("$.data.txHash").value("0xhash"));
        verify(service).getFlow(7L, 99L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void shouldRejectInvalidFlowId(String flowId) throws Exception {
        mockMvc.perform(get("/api/asset/flows/" + flowId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectFlowQueryWithoutLogin() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/asset/flows/99")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnFlowNotFoundWithoutData() throws Exception {
        when(service.getFlow(7L, 99L)).thenThrow(new BizException(404, "asset flow not found"));
        mockMvc.perform(get("/api/asset/flows/99")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404)).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReadSingleAssetForAuthenticatedUserWithNoStore() throws Exception {
        AssetAccount account = new AssetAccount();
        account.setAssetId(42L);
        account.setAvailableBalance(new java.math.BigDecimal("1.25"));
        account.setFrozenBalance(new java.math.BigDecimal("0.5"));
        account.setTotalBalance(new java.math.BigDecimal("1.75"));
        when(service.getAccount(7L, 42L)).thenReturn(account);
        mockMvc.perform(get("/api/asset/accounts/42").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.availableBalance").value(1.25))
                .andExpect(jsonPath("$.data.frozenBalance").value(0.5))
                .andExpect(jsonPath("$.data.totalBalance").value(1.75));
        verify(service).getAccount(7L, 42L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void shouldRejectInvalidAccountAssetId(String assetId) throws Exception {
        mockMvc.perform(get("/api/asset/accounts/" + assetId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectUnauthenticatedAccountQuery() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/asset/accounts/42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnAccountNotFoundWithoutData() throws Exception {
        when(service.getAccount(7L, 42L)).thenThrow(new BizException(404, "asset account not found"));
        mockMvc.perform(get("/api/asset/accounts/42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldUseAuthenticatedUserAndDefaultLimit() throws Exception {
        when(service.listFlowPage(any(), any(), any(), any(), anyInt()))
                .thenReturn(new AssetFlowPage(List.of(), null, false));
        mockMvc.perform(get("/api/asset/flows/page").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.hasMore").value(false));
        verify(service).listFlowPage(7L, null, null, null, 20);
    }

    @Test
    void shouldPreserveLargeCursorAndApplyAssetFilter() throws Exception {
        when(service.listFlowPage(any(), any(), any(), any(), anyInt()))
                .thenReturn(new AssetFlowPage(List.of(), "9007199254740993", true));
        mockMvc.perform(get("/api/asset/flows/page").param("assetId", "42")
                        .param("beforeId", "9007199254740994").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextCursor").value("9007199254740993"));
        verify(service).listFlowPage(7L, 42L, null, 9007199254740994L, 10);
    }

    @ParameterizedTest
    @EnumSource(AssetFlowBusinessType.class)
    void shouldCombineBusinessTypeWithAssetAndCursorForAuthenticatedOwner(AssetFlowBusinessType type) throws Exception {
        AssetFlow flow = new AssetFlow();
        flow.setBusinessType(type.name());
        when(service.listFlowPage(7L, 42L, type, 9007199254740994L, 10))
                .thenReturn(new AssetFlowPage(List.of(flow), "9007199254740993", true));
        mockMvc.perform(get("/api/asset/flows/page").param("userId", "999")
                        .param("assetId", "42").param("businessType", type.name())
                        .param("beforeId", "9007199254740994").param("limit", "10"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items[0].businessType").value(type.name()))
                .andExpect(jsonPath("$.data.nextCursor").value("9007199254740993"));
        verify(service).listFlowPage(7L, 42L, type, 9007199254740994L, 10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "deposit", " DEPOSIT", "UNKNOWN", "0", "DEPOSIT' OR 1=1 --"})
    void shouldRejectUnknownBusinessTypeBeforeServiceAccess(String type) throws Exception {
        mockMvc.perform(get("/api/asset/flows/page").param("businessType", type))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "abc", "9223372036854775808"})
    void shouldRejectInvalidLimit(String limit) throws Exception {
        mockMvc.perform(get("/api/asset/flows/page").param("limit", limit))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "", "9223372036854775808"})
    void shouldRejectInvalidCursorOrAsset(String value) throws Exception {
        for (String parameter : List.of("beforeId", "assetId")) {
            mockMvc.perform(get("/api/asset/flows/page").param(parameter, value))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectMissingAuthenticationBeforeDatabaseAccess() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/asset/flows/page"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service);
    }
}
