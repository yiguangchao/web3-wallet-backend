package com.example.wallet.module.deposit.controller;

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
import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.deposit.service.DepositService;
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

@WebMvcTest(DepositController.class)
@ContextConfiguration(classes = DepositController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DepositControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private DepositService service;

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
    void shouldReturnConfirmationProgressAndIgnoreRequestedUserId() throws Exception {
        DepositOrder order = new DepositOrder();
        order.setId(80L);
        order.setConfirmCount(6);
        order.setStatus(0);
        order.setTxHash("0xtransaction");
        when(service.getOrder(7L, 80L)).thenReturn(order);
        mockMvc.perform(get("/api/deposit/orders/80").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.confirmCount").value(6))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.txHash").value("0xtransaction"));
        verify(service).getOrder(7L, 80L);
    }

    @Test
    void shouldReturnNotFoundWithoutOrderData() throws Exception {
        when(service.getOrder(7L, 80L)).thenThrow(new BizException(404, "deposit order not found"));
        mockMvc.perform(get("/api/deposit/orders/80"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void shouldRejectInvalidOrderId(String orderId) throws Exception {
        mockMvc.perform(get("/api/deposit/orders/" + orderId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectUnauthenticatedLookup() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/deposit/orders/80"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void shouldKeepExistingOrderListCompatible() throws Exception {
        when(service.listOrders(7L)).thenReturn(List.of());
        mockMvc.perform(get("/api/deposit/orders"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        verify(service).listOrders(7L);
    }
}
