package com.example.wallet.module.deposit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.wallet.infrastructure.security.LoginUser;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.deposit.controller.MockDepositController;
import com.example.wallet.module.deposit.dto.MockConfirmDepositRequest;
import com.example.wallet.module.deposit.mapper.DepositOrderMapper;
import com.example.wallet.module.deposit.service.MockDepositService;
import com.example.wallet.module.deposit.service.impl.MockDepositServiceImpl;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class MockDepositIsolationTest {

    private final ApplicationContextRunner profileContextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MockDepositController.class, MockDepositServiceImpl.class)
            .withBean(DepositOrderMapper.class, () -> mock(DepositOrderMapper.class))
            .withBean(AssetService.class, () -> mock(AssetService.class))
            .withBean(Web3Service.class, () -> mock(Web3Service.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldNotRegisterMockDepositBeansByDefault() {
        profileContextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(MockDepositController.class);
            assertThat(context).doesNotHaveBean(MockDepositService.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "prod,dev", "prod,test"})
    void shouldNeverRegisterMockDepositBeansWhenProductionProfileIsActive(String profiles) {
        profileContextRunner
                .withPropertyValues("spring.profiles.active=" + profiles)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MockDepositController.class);
                    assertThat(context).doesNotHaveBean(MockDepositService.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test"})
    void shouldRegisterMockDepositBeansOnlyForNonProductionProfiles(String profile) {
        profileContextRunner
                .withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> {
                    assertThat(context).hasSingleBean(MockDepositController.class);
                    assertThat(context).hasSingleBean(MockDepositService.class);
                });
    }

    @Test
    void shouldRejectOrdinaryUserAndAllowOperator() {
        MockDepositService mockDepositService = mock(MockDepositService.class);
        ApplicationContextRunner securityContextRunner = new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=dev")
                .withUserConfiguration(MethodSecurityConfiguration.class, MockDepositController.class)
                .withBean(MockDepositService.class, () -> mockDepositService);

        securityContextRunner.run(context -> {
            MockDepositController controller = context.getBean(MockDepositController.class);
            MockConfirmDepositRequest request = new MockConfirmDepositRequest();

            authenticate(new LoginUser(1L, "user", "USER"), "ROLE_USER");
            assertThatThrownBy(() -> controller.mockConfirm(request))
                    .isInstanceOf(AccessDeniedException.class);

            authenticate(new LoginUser(2L, "operator", "OPERATOR"), "ROLE_OPERATOR");
            when(mockDepositService.mockConfirm(2L, request)).thenReturn(99L);

            assertThat(controller.mockConfirm(request).getData()).isEqualTo(99L);
            verify(mockDepositService).mockConfirm(2L, request);
        });
    }

    private void authenticate(LoginUser principal, String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(authority))));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
