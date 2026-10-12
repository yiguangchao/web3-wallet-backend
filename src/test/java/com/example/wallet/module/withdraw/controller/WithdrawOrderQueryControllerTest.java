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
