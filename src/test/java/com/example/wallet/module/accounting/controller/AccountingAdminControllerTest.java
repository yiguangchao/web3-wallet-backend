package com.example.wallet.module.accounting.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.wallet.module.accounting.entity.AccountingJournal;
import com.example.wallet.module.accounting.model.AccountingJournalView;
import com.example.wallet.module.accounting.service.AccountingQueryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AccountingAdminControllerTest {

    @Mock private AccountingQueryService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AccountingAdminController(service)).build();
    }

    @Test
    void shouldRouteBusinessIdentityLookup() throws Exception {
        when(service.getByBusiness("DEPOSIT", 501L)).thenReturn(view());

        mockMvc.perform(get("/api/admin/accounting/journals/DEPOSIT/501"))
                .andExpect(status().isOk());

        verify(service).getByBusiness("DEPOSIT", 501L);
    }

    @Test
    void shouldPreferSourceFlowRouteOverBusinessRoute() throws Exception {
        when(service.getBySourceFlowId(701L)).thenReturn(view());

        mockMvc.perform(get("/api/admin/accounting/journals/by-flow/701"))
                .andExpect(status().isOk());

        verify(service).getBySourceFlowId(701L);
    }

    private AccountingJournalView view() {
        AccountingJournal journal = new AccountingJournal();
        journal.setId(901L);
        return new AccountingJournalView(journal, List.of());
    }
}
