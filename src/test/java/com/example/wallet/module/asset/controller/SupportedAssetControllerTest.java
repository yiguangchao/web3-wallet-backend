package com.example.wallet.module.asset.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.wallet.module.asset.service.SupportedAssetService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SupportedAssetController.class)
@ContextConfiguration(classes = SupportedAssetController.class)
@AutoConfigureMockMvc(addFilters = false)
class SupportedAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupportedAssetService supportedAssetService;

    @Test
    void shouldReturnEmptyCatalogWithNoStoreHeader() throws Exception {
        when(supportedAssetService.listSupportedAssets()).thenReturn(List.of());

        mockMvc.perform(get("/api/asset/supported"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
