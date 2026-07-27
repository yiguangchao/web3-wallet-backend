package com.example.wallet.module.deposit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.module.deposit.entity.DepositOrder;
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
    public void listenDeposits() {
        depositBlockScanner.scan();
    }
}
