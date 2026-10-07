package com.example.wallet.module.asset.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.asset.entity.AssetFlow;
import com.example.wallet.module.asset.mapper.AssetAccountMapper;
import com.example.wallet.module.asset.mapper.AssetFlowMapper;
import com.example.wallet.module.asset.mapper.AssetFreezeDetailMapper;
import com.example.wallet.module.asset.mapper.AssetRiskFreezeDetailMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetFlowPaginationTest {

    private AssetFlowMapper mapper;
    private AssetServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AssetFlow.class);
        mapper = mock(AssetFlowMapper.class);
        service = new AssetServiceImpl(mock(AssetAccountMapper.class), mapper,
                mock(AssetFreezeDetailMapper.class), mock(AssetRiskFreezeDetailMapper.class));
    }

    @Test
    void shouldScopeQueryToUserAssetAndExclusiveCursorAndTrimLookahead() {
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<AssetFlow> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("user_id =", "asset_id =", "id <",
                    "ORDER BY id DESC", "LIMIT 3");
            assertThat(query.getParamNameValuePairs().values()).containsExactlyInAnyOrder(7L, 42L, 100L);
            return List.of(flow(99L), flow(98L), flow(97L));
        });

        var page = service.listFlowPage(7L, 42L, 100L, 2);
        assertThat(page.items()).extracting(AssetFlow::getId).containsExactly(99L, 98L);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("98");
    }

    @Test
    void shouldReturnNoCursorForLastPageAndAvoidOptionalFilters() {
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<AssetFlow> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("user_id =", "LIMIT 3")
                    .doesNotContain("asset_id =", "id <");
            return List.of(flow(99L), flow(98L));
        });
        var page = service.listFlowPage(7L, null, null, 2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void shouldReturnEmptyPage() {
        when(mapper.selectList(any())).thenReturn(List.of());
        var page = service.listFlowPage(7L, null, null, 20);
        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void shouldRejectInvalidParametersBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.listFlowPage(null, null, null, 20)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.listFlowPage(7L, null, null, 0)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.listFlowPage(7L, null, null, 101)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.listFlowPage(7L, 0L, null, 20)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.listFlowPage(7L, null, -1L, 20)).isInstanceOf(BizException.class);
        verifyNoInteractions(mapper);
    }

    private AssetFlow flow(long id) {
        AssetFlow flow = new AssetFlow();
        flow.setId(id);
        return flow;
    }
}
