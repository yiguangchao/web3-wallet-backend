package com.example.wallet.module.withdraw.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.wallet.common.api.AuditActor;
import com.example.wallet.common.api.AuditActorProvider;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.withdraw.entity.WithdrawOperationLog;
import com.example.wallet.module.withdraw.mapper.WithdrawOperationLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawAuditServiceImplTest {

    @Mock
    private WithdrawOperationLogMapper logMapper;
    @Mock
    private AuditActorProvider actorProvider;

    private WithdrawAuditServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WithdrawAuditServiceImpl(logMapper, actorProvider);
        when(actorProvider.current()).thenReturn(
                new AuditActor(10L, "admin", "ADMIN", "127.0.0.1"));
    }

    @Test
    void shouldPersistAdministratorAuditIdentity() {
        when(logMapper.insert(any(WithdrawOperationLog.class))).thenReturn(1);

        service.record(99L, "APPROVE", 0, 6, "approved");

        ArgumentCaptor<WithdrawOperationLog> captor = ArgumentCaptor.forClass(WithdrawOperationLog.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperatorUserId()).isEqualTo(10L);
        assertThat(captor.getValue().getOperatorUsername()).isEqualTo("admin");
        assertThat(captor.getValue().getOperatorRole()).isEqualTo("ADMIN");
        assertThat(captor.getValue().getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldRejectAuditInsertWhenNoRowWasWritten() {
        when(logMapper.insert(any(WithdrawOperationLog.class))).thenReturn(0);

        assertThatThrownBy(() -> service.record(99L, "APPROVE", 0, 6, "approved"))
                .isInstanceOf(BizException.class)
                .hasMessage("withdraw audit log creation failed");
    }
}
