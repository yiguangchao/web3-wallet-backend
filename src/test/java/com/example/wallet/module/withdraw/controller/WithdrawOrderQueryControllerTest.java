package com.example.wallet.module.withdraw.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.wallet.common.exception.BizException;
import com.example.wallet.common.exception.GlobalExceptionHandler;
import com.example.wallet.infrastructure.security.LoginUser;
import com.example.wallet.module.withdraw.dto.WithdrawOrderResponse;
import com.example.wallet.module.withdraw.dto.WithdrawOrderPage;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.service.WithdrawService;
import java.math.BigDecimal;
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

@WebMvcTest(WithdrawController.class)
@ContextConfiguration(classes = WithdrawController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class WithdrawOrderQueryControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private WithdrawService service;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(7L, "alice", "USER"), null, List.of()));
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void shouldReturnDefaultPageForAuthenticatedOwner() throws Exception {
        when(service.listOrderPage(7L, null, null, null, 20))
                .thenReturn(new WithdrawOrderPage(List.of(), null, false));
        mockMvc.perform(get("/api/withdraw/orders/page").param("userId", "999"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.hasMore").value(false));
        verify(service).listOrderPage(7L, null, null, null, 20);
    }

    @Test
    void shouldReturnFilteredPageWithExactIdsAndNoOperationalFields() throws Exception {
        var response = new WithdrawOrderResponse("9007199254740993", "request-1", "7001", 11155111L,
                "ETH", "ETH", null, 18, "0xrecipient", BigDecimal.ONE, BigDecimal.ZERO,
                2, "BROADCASTED", "0xhash", null, null, null);
        when(service.listOrderPage(7L, 7001L, WithdrawStatus.BROADCASTED, 9007199254740994L, 1))
                .thenReturn(new WithdrawOrderPage(List.of(response), "9007199254740993", true));
        mockMvc.perform(get("/api/withdraw/orders/page").param("assetId", "7001")
                        .param("status", "BROADCASTED").param("beforeId", "9007199254740994").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value("9007199254740993"))
                .andExpect(jsonPath("$.data.items[0].assetId").value("7001"))
                .andExpect(jsonPath("$.data.items[0].statusName").value("BROADCASTED"))
                .andExpect(jsonPath("$.data.items[0].signerKeyId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].hotWalletAddress").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].nonce").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].reviewerUserId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].operatorUserId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].manualReviewReason").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor").value("9007199254740993"))
                .andExpect(jsonPath("$.data.hasMore").value(true));
        verify(service).listOrderPage(7L, 7001L, WithdrawStatus.BROADCASTED, 9007199254740994L, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"assetId=0", "assetId=abc", "assetId=", "beforeId=-1", "beforeId=",
            "beforeId=9223372036854775808", "limit=0", "limit=101", "limit=abc",
            "limit=9223372036854775808", "status=", "status=unknown", "status=2", "status=confirmed"})
    void shouldRejectInvalidPagination(String parameter) throws Exception {
        String[] parts = parameter.split("=", -1);
        mockMvc.perform(get("/api/withdraw/orders/page").param(parts[0], parts[1]))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void shouldRequireLoginForPagination() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/withdraw/orders/page"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnPublicProgressWithExactLargeIdAndNoOperationalFields() throws Exception {
        var response = new WithdrawOrderResponse("9007199254740993", "request-1", "7001", 11155111L,
                "ETH", "ETH", null, 18, "0xrecipient", BigDecimal.ONE, BigDecimal.ZERO,
                2, "BROADCASTED", "0xhash", null, null, null);
        when(service.getOrder(7L, 9007199254740993L)).thenReturn(response);
        mockMvc.perform(get("/api/withdraw/orders/9007199254740993").param("userId", "999"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("9007199254740993"))
                .andExpect(jsonPath("$.data.statusName").value("BROADCASTED"))
                .andExpect(jsonPath("$.data.txHash").value("0xhash"))
                .andExpect(jsonPath("$.data.signerKeyId").doesNotExist())
                .andExpect(jsonPath("$.data.hotWalletAddress").doesNotExist())
                .andExpect(jsonPath("$.data.nonce").doesNotExist())
                .andExpect(jsonPath("$.data.reviewerUserId").doesNotExist())
                .andExpect(jsonPath("$.data.operatorUserId").doesNotExist())
                .andExpect(jsonPath("$.data.manualReviewReason").doesNotExist());
        verify(service).getOrder(7L, 9007199254740993L);
    }

    @Test
    void shouldReturnNotFoundWithoutData() throws Exception {
        when(service.getOrder(7L, 80L)).thenThrow(new BizException(404, "withdrawal order not found"));
        mockMvc.perform(get("/api/withdraw/orders/80")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404)).andExpect(jsonPath("$.data").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void shouldRejectInvalidIdBeforeQuery(String orderId) throws Exception {
        mockMvc.perform(get("/api/withdraw/orders/" + orderId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void shouldRequireLogin() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/withdraw/orders/80")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void shouldKeepListCompatible() throws Exception {
        when(service.listOrders(7L)).thenReturn(List.of());
        mockMvc.perform(get("/api/withdraw/orders")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(service).listOrders(7L);
    }
}
