package com.example.wallet.module.user.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.security.JwtTokenProvider;
import com.example.wallet.module.user.dto.LoginRequest;
import com.example.wallet.module.user.entity.SysUser;
import com.example.wallet.module.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String DUMMY_PASSWORD_HASH = "dummy-password-hash";

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("not-a-user-credential")).thenReturn(DUMMY_PASSWORD_HASH);
        userService = new UserServiceImpl(userMapper, passwordEncoder, jwtTokenProvider);
    }

    @Test
    void shouldReadCurrentProfileWithoutPasswordOrTokenCreation() {
        SysUser user = new SysUser();
        user.setId(9007199254740993L);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setRole("REVIEWER");
        user.setStatus(1);
        user.setPassword("private-hash");
        when(userMapper.selectById(user.getId())).thenReturn(user);

        var profile = userService.getProfile(user.getId());
        assertThat(profile.id()).isEqualTo("9007199254740993");
        assertThat(profile.username()).isEqualTo("alice");
        assertThat(profile.email()).isEqualTo("alice@example.com");
        assertThat(profile.role()).isEqualTo("REVIEWER");
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void shouldRejectDeletedAndDisabledAccountsIdentically() {
        SysUser disabled = new SysUser();
        disabled.setStatus(0);
        when(userMapper.selectById(7L)).thenReturn(null, disabled);
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> userService.getProfile(7L))
                    .isInstanceOf(BizException.class).hasMessage("账号不可用，请重新登录")
                    .extracting("code").isEqualTo(401);
        }
    }

    @Test
    void shouldRejectInvalidProfileIdentityBeforeQuery() {
        assertThatThrownBy(() -> userService.getProfile(null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> userService.getProfile(0L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> userService.getProfile(-1L)).isInstanceOf(BizException.class);
        verifyNoInteractions(userMapper, jwtTokenProvider);
    }

    @Test
    void shouldPerformPasswordCheckWhenUsernameDoesNotExist() {
        LoginRequest request = new LoginRequest();
        request.setUsername("unknown-user");
        request.setPassword("wrong-password");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(passwordEncoder.matches("wrong-password", DUMMY_PASSWORD_HASH)).thenReturn(false);

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BizException.class)
                .hasMessage("用户名或密码错误");

        verify(passwordEncoder).matches("wrong-password", DUMMY_PASSWORD_HASH);
        verifyNoInteractions(jwtTokenProvider);
    }
}
