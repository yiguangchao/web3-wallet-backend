package com.example.wallet.module.deposit.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.deposit.mapper.DepositOrderMapper;
import com.example.wallet.module.deposit.scanner.DepositBlockScanner;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DepositServiceImplTest {
    private DepositOrderMapper mapper;
    private DepositServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DepositOrder.class);
        mapper = mock(DepositOrderMapper.class);
        service = new DepositServiceImpl(mapper, mock(DepositBlockScanner.class));
    }

    @Test
    void shouldQueryByOrderAndAuthenticatedOwnerTogether() {
        DepositOrder order = new DepositOrder();
        order.setId(80L);
        order.setUserId(7L);
        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<DepositOrder> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("id =", "user_id =");
            assertThat(query.getParamNameValuePairs().values()).containsExactlyInAnyOrder(80L, 7L);
            return order;
        });
        assertThat(service.getOrder(7L, 80L)).isSameAs(order);
    }

    @Test
    void shouldReturnSameNotFoundErrorForMissingOrUnownedOrder() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.getOrder(7L, 80L))
                .isInstanceOf(BizException.class).hasMessage("deposit order not found")
                .extracting("code").isEqualTo(404);
    }

    @Test
    void shouldRejectInvalidIdentitiesBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.getOrder(null, 80L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.getOrder(0L, 80L)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.getOrder(7L, null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.getOrder(7L, -1L)).isInstanceOf(BizException.class);
        verifyNoInteractions(mapper);
    }
}
