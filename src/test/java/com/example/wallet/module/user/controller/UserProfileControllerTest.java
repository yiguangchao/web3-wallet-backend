package com.example.wallet.module.user.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.wallet.common.config.RestOnlyMvcGuard;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.common.exception.GlobalExceptionHandler;
import com.example.wallet.infrastructure.security.LoginUser;
import com.example.wallet.module.user.dto.UserProfileResponse;
import com.example.wallet.module.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserProfileController.class)
@ContextConfiguration(classes = UserProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, RestOnlyMvcGuard.class})
class UserProfileControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UserService service;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(7L, "alice", "USER"), null, List.of()));
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void shouldUseAuthenticatedIdentityAndReturnCurrentRoleWithoutSecrets() throws Exception {
        when(service.getProfile(7L)).thenReturn(new UserProfileResponse("7", "alice",
                "alice@example.com", "REVIEWER", null));
        mockMvc.perform(get("/api/user/me").param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("7"))
                .andExpect(jsonPath("$.data.role").value("REVIEWER"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist());
        verify(service).getProfile(7L);
    }

    @Test
    void shouldRejectMissingLoginBeforeDatabaseAccess() throws Exception {
        SecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/user/me")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnUnavailableAccountErrorWithoutProfile() throws Exception {
        when(service.getProfile(7L)).thenThrow(new BizException(401, "账号不可用，请重新登录"));
        mockMvc.perform(get("/api/user/me")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
