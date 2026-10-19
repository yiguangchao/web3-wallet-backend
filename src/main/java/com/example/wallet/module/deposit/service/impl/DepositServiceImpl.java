package com.example.wallet.module.deposit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.deposit.dto.DepositOrderPage;
import com.example.wallet.module.deposit.mapper.DepositOrderMapper;
import com.example.wallet.module.deposit.scanner.DepositBlockScanner;
import com.example.wallet.module.deposit.service.DepositService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DepositServiceImpl implements DepositService {

    private final DepositOrderMapper depositOrderMapper;
    private final DepositBlockScanner depositBlockScanner;

    public DepositServiceImpl(DepositOrderMapper depositOrderMapper, DepositBlockScanner depositBlockScanner) {
        this.depositOrderMapper = depositOrderMapper;
        this.depositBlockScanner = depositBlockScanner;
    }

    @Override
    public List<DepositOrder> listOrders(Long userId) {
        return depositOrderMapper.selectList(new LambdaQueryWrapper<DepositOrder>()
                .eq(DepositOrder::getUserId, userId)
                .orderByDesc(DepositOrder::getCreatedAt));
    }

    @Override
    public DepositOrderPage listOrderPage(Long userId, Long assetId, Long beforeId, int limit) {
        if (userId == null || userId <= 0 || limit < 1 || limit > 100
                || (assetId != null && assetId <= 0) || (beforeId != null && beforeId <= 0)) {
            throw new BizException(400, "invalid deposit order pagination parameters");
        }
        List<DepositOrder> rows = depositOrderMapper.selectList(new LambdaQueryWrapper<DepositOrder>()
                .eq(DepositOrder::getUserId, userId)
                .eq(assetId != null, DepositOrder::getAssetId, assetId)
                .lt(beforeId != null, DepositOrder::getId, beforeId)
                .orderByDesc(DepositOrder::getId)
                .last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        List<DepositOrder> items = List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
        String nextCursor = hasMore ? items.get(items.size() - 1).getId().toString() : null;
        return new DepositOrderPage(items, nextCursor, hasMore);
    }

    @Override
    public DepositOrder getOrder(Long userId, Long orderId) {
        if (userId == null || userId <= 0 || orderId == null || orderId <= 0) {
            throw new BizException(400, "invalid deposit order identity");
        }
        DepositOrder order = depositOrderMapper.selectOne(new LambdaQueryWrapper<DepositOrder>()
                .eq(DepositOrder::getId, orderId)
                .eq(DepositOrder::getUserId, userId));
        if (order == null) {
            throw new BizException(404, "deposit order not found");
        }
        return order;
    }

    @Override
    public void listenDeposits() {
        depositBlockScanner.scan();
    }
}
