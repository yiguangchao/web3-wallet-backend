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
import com.example.wallet.module.asset.service.AssetService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    void shouldUseAuthenticatedUserAndDefaultLimit() throws Exception {
        when(service.listFlowPage(any(), any(), any(), anyInt()))
                .thenReturn(new AssetFlowPage(List.of(), null, false));
        mockMvc.perform(get("/api/asset/flows/page").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.hasMore").value(false));
        verify(service).listFlowPage(7L, null, null, 20);
    }

    @Test
    void shouldPreserveLargeCursorAndApplyAssetFilter() throws Exception {
        when(service.listFlowPage(any(), any(), any(), anyInt()))
                .thenReturn(new AssetFlowPage(List.of(), "9007199254740993", true));
        mockMvc.perform(get("/api/asset/flows/page").param("assetId", "42")
                        .param("beforeId", "9007199254740994").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextCursor").value("9007199254740993"));
        verify(service).listFlowPage(7L, 42L, 9007199254740994L, 10);
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
