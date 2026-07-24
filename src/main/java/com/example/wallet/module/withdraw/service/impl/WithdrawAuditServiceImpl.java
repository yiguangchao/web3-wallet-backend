package com.example.wallet.module.withdraw.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.api.AuditActor;
import com.example.wallet.common.api.AuditActorProvider;
import com.example.wallet.module.withdraw.entity.WithdrawOperationLog;
import com.example.wallet.module.withdraw.mapper.WithdrawOperationLogMapper;
import com.example.wallet.module.withdraw.service.WithdrawAuditService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WithdrawAuditServiceImpl implements WithdrawAuditService {

    private final WithdrawOperationLogMapper logMapper;
    private final AuditActorProvider actorProvider;

    public WithdrawAuditServiceImpl(WithdrawOperationLogMapper logMapper, AuditActorProvider actorProvider) {
        this.logMapper = logMapper;
        this.actorProvider = actorProvider;
    }

    @Override
    public void record(Long orderId, String action, Integer beforeStatus, Integer afterStatus, String remark) {
        AuditActor actor = actorProvider.current();
        WithdrawOperationLog log = new WithdrawOperationLog();
        log.setOrderId(orderId);
        log.setAction(action);
        log.setOperatorUserId(actor.userId());
        log.setOperatorUsername(actor.username());
        log.setOperatorRole(actor.role());
        log.setIpAddress(actor.ipAddress());
        log.setBeforeStatus(beforeStatus);
        log.setAfterStatus(afterStatus);
        log.setRemark(remark);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    @Override
    public List<WithdrawOperationLog> listByOrderId(Long orderId) {
        return logMapper.selectList(new LambdaQueryWrapper<WithdrawOperationLog>()
                .eq(WithdrawOperationLog::getOrderId, orderId)
                .orderByDesc(WithdrawOperationLog::getCreatedAt));
    }
}
