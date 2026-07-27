package com.example.wallet.module.wallet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.custody.CustodyWalletProperties;
import com.example.wallet.module.deposit.config.DepositScanProperties;
import com.example.wallet.module.deposit.config.DepositScanProperties.Token;
import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.wallet.entity.CustodyDepositAddress;
import com.example.wallet.module.wallet.entity.CustodySweepOrder;
import com.example.wallet.module.wallet.entity.CustodySweepStatus;
import com.example.wallet.module.wallet.mapper.CustodyDepositAddressMapper;
import com.example.wallet.module.wallet.mapper.CustodySweepOrderMapper;
import com.example.wallet.module.wallet.service.CustodySweepService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CustodySweepServiceImpl implements CustodySweepService {

    private final CustodySweepOrderMapper sweepOrderMapper;
    private final CustodyDepositAddressMapper depositAddressMapper;
    private final CustodyWalletProperties custodyProperties;
    private final DepositScanProperties scanProperties;

    public CustodySweepServiceImpl(CustodySweepOrderMapper sweepOrderMapper,
                                   CustodyDepositAddressMapper depositAddressMapper,
                                   CustodyWalletProperties custodyProperties,
                                   DepositScanProperties scanProperties) {
        this.sweepOrderMapper = sweepOrderMapper;
        this.depositAddressMapper = depositAddressMapper;
        this.custodyProperties = custodyProperties;
        this.scanProperties = scanProperties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void schedule(DepositOrder depositOrder) {
        if (!custodyProperties.isEnabled() || !custodyProperties.getSweep().isEnabled()) {
            return;
        }
        String collectionAddress = custodyProperties.getSweep().getCollectionAddress();
        if (!validAddress(collectionAddress)) {
            throw new BizException("custody collection address is not configured");
        }
        CustodyDepositAddress address = depositAddressMapper.selectOne(
                new LambdaQueryWrapper<CustodyDepositAddress>()
                        .eq(CustodyDepositAddress::getChain, depositOrder.getChain())
                        .eq(CustodyDepositAddress::getAddress, depositOrder.getToAddress().toLowerCase(Locale.ROOT)));
        if (address == null) {
            throw new BizException("confirmed deposit does not belong to a custody address");
        }
        boolean exists = sweepOrderMapper.selectCount(new LambdaQueryWrapper<CustodySweepOrder>()
                .eq(CustodySweepOrder::getDepositOrderId, depositOrder.getId())) > 0;
        if (exists) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        CustodySweepOrder order = new CustodySweepOrder();
        order.setDepositOrderId(depositOrder.getId());
        order.setAddressId(address.getId());
        order.setUserId(depositOrder.getUserId());
        order.setChain(depositOrder.getChain());
        order.setTokenSymbol(depositOrder.getTokenSymbol());
        order.setTokenAddress(normalizeNullable(depositOrder.getTokenAddress()));
        order.setTokenDecimals(resolveDecimals(depositOrder.getTokenAddress()));
        order.setFromAddress(address.getAddress());
        order.setToAddress(collectionAddress.toLowerCase(Locale.ROOT));
        order.setDerivationIndex(address.getDerivationIndex());
        order.setKeyVersion(address.getKeyVersion());
        order.setDetectedAmount(depositOrder.getAmount());
        order.setStatus(CustodySweepStatus.PENDING.getCode());
        order.setAttemptCount(0);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        sweepOrderMapper.insert(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Optional<CustodySweepOrder> claimNext() {
        LocalDateTime now = LocalDateTime.now();
        CustodySweepOrder order = sweepOrderMapper.selectOne(
                new LambdaQueryWrapper<CustodySweepOrder>()
                        .and(wrapper -> wrapper
                                .eq(CustodySweepOrder::getStatus, CustodySweepStatus.PENDING.getCode())
                                .or(retry -> retry
                                        .eq(CustodySweepOrder::getStatus, CustodySweepStatus.FAILED.getCode())
                                        .lt(CustodySweepOrder::getAttemptCount,
                                                custodyProperties.getSweep().getMaxAttempts())
                                        .and(time -> time.isNull(CustodySweepOrder::getNextRetryAt)
                                                .or().le(CustodySweepOrder::getNextRetryAt, now))))
                        .orderByAsc(CustodySweepOrder::getCreatedAt)
                        .last("LIMIT 1 FOR UPDATE"));
        if (order == null) {
            return Optional.empty();
        }
        order.setStatus(CustodySweepStatus.PROCESSING.getCode());
        order.setAttemptCount(order.getAttemptCount() + 1);
        order.setNextRetryAt(null);
        order.setLastError(null);
        order.setUpdatedAt(now);
        sweepOrderMapper.updateById(order);
        return Optional.of(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverStaleProcessing() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minus(Duration.ofMillis(
                custodyProperties.getSweep().getProcessingTimeout()));
        sweepOrderMapper.update(null, new LambdaUpdateWrapper<CustodySweepOrder>()
                .eq(CustodySweepOrder::getStatus, CustodySweepStatus.PROCESSING.getCode())
                .lt(CustodySweepOrder::getUpdatedAt, cutoff)
                .set(CustodySweepOrder::getStatus, CustodySweepStatus.FAILED.getCode())
                .set(CustodySweepOrder::getNextRetryAt, now)
                .set(CustodySweepOrder::getLastError, "recovered stale processing sweep")
                .set(CustodySweepOrder::getUpdatedAt, now));
    }

    @Override
    public List<CustodySweepOrder> listBroadcasted(int limit) {
        return sweepOrderMapper.selectList(new LambdaQueryWrapper<CustodySweepOrder>()
                .eq(CustodySweepOrder::getStatus, CustodySweepStatus.BROADCASTED.getCode())
                .orderByAsc(CustodySweepOrder::getUpdatedAt)
                .last("LIMIT " + Math.max(1, limit)));
    }

    @Override
    public List<CustodySweepOrder> listRecent() {
        return sweepOrderMapper.selectList(new LambdaQueryWrapper<CustodySweepOrder>()
                .orderByDesc(CustodySweepOrder::getCreatedAt)
                .last("LIMIT 100"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markBroadcasted(Long orderId, String txHash, BigDecimal sweptAmount) {
        CustodySweepOrder order = requireOrder(orderId);
        order.setTxHash(txHash);
        order.setSweptAmount(sweptAmount);
        order.setStatus(CustodySweepStatus.BROADCASTED.getCode());
        order.setUpdatedAt(LocalDateTime.now());
        sweepOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markConfirmed(Long orderId) {
        CustodySweepOrder order = requireOrder(orderId);
        order.setStatus(CustodySweepStatus.CONFIRMED.getCode());
        order.setUpdatedAt(LocalDateTime.now());
        sweepOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markSkipped(Long orderId, String reason) {
        CustodySweepOrder order = requireOrder(orderId);
        order.setStatus(CustodySweepStatus.SKIPPED.getCode());
        order.setLastError(truncate(reason));
        order.setUpdatedAt(LocalDateTime.now());
        sweepOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long orderId, String error) {
        CustodySweepOrder order = requireOrder(orderId);
        order.setStatus(CustodySweepStatus.FAILED.getCode());
        order.setLastError(truncate(error));
        if (order.getAttemptCount() < custodyProperties.getSweep().getMaxAttempts()) {
            order.setNextRetryAt(LocalDateTime.now().plus(
                    Duration.ofMillis(custodyProperties.getSweep().getRetryDelay())));
        } else {
            order.setNextRetryAt(null);
        }
        order.setUpdatedAt(LocalDateTime.now());
        sweepOrderMapper.updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retry(Long orderId) {
        CustodySweepOrder order = requireOrder(orderId);
        if (order.getStatus().equals(CustodySweepStatus.BROADCASTED.getCode())
                || order.getStatus().equals(CustodySweepStatus.CONFIRMED.getCode())) {
            throw new BizException("broadcasted or confirmed sweep cannot be retried");
        }
        order.setStatus(CustodySweepStatus.PENDING.getCode());
        order.setAttemptCount(0);
        order.setNextRetryAt(null);
        order.setLastError(null);
        order.setUpdatedAt(LocalDateTime.now());
        sweepOrderMapper.updateById(order);
    }

    private CustodySweepOrder requireOrder(Long orderId) {
        CustodySweepOrder order = sweepOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException("custody sweep order not found");
        }
        return order;
    }

    private int resolveDecimals(String tokenAddress) {
        if (!StringUtils.hasText(tokenAddress)) {
            return 18;
        }
        return scanProperties.getScan().getTokens().stream()
                .filter(token -> token.getAddress() != null
                        && token.getAddress().equalsIgnoreCase(tokenAddress))
                .map(Token::getDecimals)
                .findFirst()
                .orElseThrow(() -> new BizException("token decimals are not configured for custody sweep"));
    }

    private String normalizeNullable(String address) {
        return StringUtils.hasText(address) ? address.toLowerCase(Locale.ROOT) : null;
    }

    private boolean validAddress(String address) {
        return address != null && address.matches("^0x[0-9a-fA-F]{40}$");
    }

    private String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown sweep error";
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }
}
