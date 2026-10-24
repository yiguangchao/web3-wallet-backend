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
import java.util.List;
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
    void shouldFilterByOwnerAssetAndExclusiveCursorAndTrimLookahead() {
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<DepositOrder> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("user_id =", "asset_id =", "id <",
                    "ORDER BY id DESC", "LIMIT 3");
            assertThat(query.getParamNameValuePairs().values()).containsExactlyInAnyOrder(7L, 42L, 100L);
            return List.of(order(99L), order(98L), order(97L));
        });
        var page = service.listOrderPage(7L, 42L, 100L, 2);
        assertThat(page.items()).extracting(DepositOrder::getId).containsExactly(99L, 98L);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("98");
    }

    @Test
    void shouldOmitOptionalFiltersAndReturnNoCursorForExactLastPage() {
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<DepositOrder> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("user_id =", "LIMIT 3")
                    .doesNotContain("asset_id =", "id <");
            return List.of(order(99L), order(98L));
        });
        var page = service.listOrderPage(7L, null, null, 2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void shouldReturnEmptyPage() {
        when(mapper.selectList(any())).thenReturn(List.of());
        var page = service.listOrderPage(7L, null, null, 20);
        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void shouldPreserveLargeCursorExactly() {
        when(mapper.selectList(any())).thenReturn(List.of(order(9007199254740993L), order(80L)));
        assertThat(service.listOrderPage(7L, null, null, 1).nextCursor()).isEqualTo("9007199254740993");
    }

    @Test
    void shouldRejectInvalidPaginationBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.listOrderPage(null, null, null, 20)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.listOrderPage(0L, null, null, 20)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.listOrderPage(7L, 0L, null, 20)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.listOrderPage(7L, null, -1L, 20)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.listOrderPage(7L, null, null, 0)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.listOrderPage(7L, null, null, 101)).isInstanceOf(BizException.class);
        verifyNoInteractions(mapper);
    }

    private DepositOrder order(long id) {
        DepositOrder order = new DepositOrder();
        order.setId(id);
        return order;
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
