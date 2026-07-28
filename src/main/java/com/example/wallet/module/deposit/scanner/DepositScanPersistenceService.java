package com.example.wallet.module.deposit.scanner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.chain.entity.ChainBlockScanRecord;
import com.example.wallet.module.chain.mapper.ChainBlockScanRecordMapper;
import com.example.wallet.module.deposit.config.DepositScanProperties;
import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.deposit.mapper.DepositOrderMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DepositScanPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(DepositScanPersistenceService.class);

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_CONFIRMED = 1;
    public static final int STATUS_REORGED = 2;

    private final ChainBlockScanRecordMapper scanRecordMapper;
    private final DepositOrderMapper depositOrderMapper;
    private final AssetService assetService;
    private final SupportedAssetService supportedAssetService;
    private final DepositScanProperties properties;

    public DepositScanPersistenceService(ChainBlockScanRecordMapper scanRecordMapper,
                                         DepositOrderMapper depositOrderMapper,
                                         AssetService assetService,
                                         SupportedAssetService supportedAssetService,
                                         DepositScanProperties properties) {
        this.scanRecordMapper = scanRecordMapper;
        this.depositOrderMapper = depositOrderMapper;
        this.assetService = assetService;
        this.supportedAssetService = supportedAssetService;
        this.properties = properties;
    }

    @Transactional(rollbackFor = Exception.class)
    public ChainBlockScanRecord getOrCreateRecord() {
        String chain = properties.getScan().getChain();
        ChainBlockScanRecord record = scanRecordMapper.selectOne(new LambdaQueryWrapper<ChainBlockScanRecord>()
                .eq(ChainBlockScanRecord::getChain, chain));
        if (record != null) {
            return record;
        }
        LocalDateTime now = LocalDateTime.now();
        record = new ChainBlockScanRecord();
        record.setChain(chain);
        record.setLastScannedBlock(properties.getScan().getInitialBlock().subtract(BigInteger.ONE));
        record.setConfirmedBlock(BigInteger.ZERO);
        record.setStatus(1);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        scanRecordMapper.insert(record);
        return record;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveBatch(List<DetectedDeposit> deposits, BigInteger blockNumber, String blockHash) {
        LocalDateTime now = LocalDateTime.now();
        for (DetectedDeposit detected : deposits) {
            SupportedAsset asset = supportedAssetService.getRequiredDepositAsset(detected.assetId());
            validateDetectedAsset(detected, asset);
            if (detected.amount().compareTo(asset.getMinDeposit()) < 0) {
                log.warn("Ignoring deposit below minimum: assetCode={}, txHash={}, logIndex={}, amount={}",
                        asset.getAssetCode(), detected.txHash(), detected.logIndex(), detected.amount());
                continue;
            }
            boolean exists = depositOrderMapper.selectCount(new LambdaQueryWrapper<DepositOrder>()
                    .eq(DepositOrder::getChain, detected.chain())
                    .eq(DepositOrder::getTxHash, detected.txHash())
                    .eq(DepositOrder::getLogIndex, detected.logIndex())) > 0;
            if (exists) {
                continue;
            }
            DepositOrder order = new DepositOrder();
            order.setUserId(detected.userId());
            order.setAssetId(asset.getId());
            order.setChain(asset.getChain());
            order.setTokenSymbol(asset.getSymbol());
            order.setTokenAddress(asset.getTokenAddress());
            order.setFromAddress(detected.fromAddress());
            order.setToAddress(detected.toAddress());
            order.setAmount(detected.amount());
            order.setTxHash(detected.txHash());
            order.setLogIndex(detected.logIndex());
            order.setBlockNumber(detected.blockNumber());
            order.setBlockHash(detected.blockHash());
            order.setConfirmCount(0);
            order.setStatus(STATUS_PENDING);
            order.setSweepTaskStatus(0);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            depositOrderMapper.insert(order);
        }
        ChainBlockScanRecord record = getOrCreateRecord();
        record.setLastScannedBlock(blockNumber);
        record.setLastScannedBlockHash(blockHash);
        record.setUpdatedAt(now);
        scanRecordMapper.updateById(record);
    }

    public List<DepositOrder> listPendingOrders() {
        return depositOrderMapper.selectList(new LambdaQueryWrapper<DepositOrder>()
                .eq(DepositOrder::getChain, properties.getScan().getChain())
                .eq(DepositOrder::getStatus, STATUS_PENDING));
    }

    public List<DepositOrder> listConfirmedAfter(BigInteger blockNumber) {
        return depositOrderMapper.selectList(new LambdaQueryWrapper<DepositOrder>()
                .eq(DepositOrder::getChain, properties.getScan().getChain())
                .eq(DepositOrder::getStatus, STATUS_CONFIRMED)
                .gt(DepositOrder::getBlockNumber, blockNumber));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateConfirmation(Long orderId, int confirmCount, boolean canonical) {
        DepositOrder order = depositOrderMapper.selectById(orderId);
        if (order == null || order.getStatus() != STATUS_PENDING) {
            return;
        }
        if (!canonical) {
            order.setStatus(STATUS_REORGED);
        } else {
            SupportedAsset asset = supportedAssetService.getRequiredDepositAsset(order.getAssetId());
            int requiredConfirmations = asset.getConfirmationBlocks();
            if (confirmCount >= requiredConfirmations) {
                int updated = depositOrderMapper.markConfirmedIfPending(orderId, confirmCount, LocalDateTime.now());
                if (updated == 0) {
                    return;
                }
                assetService.creditDeposit(order.getUserId(), asset,
                        order.getAmount(), order.getId(), order.getTxHash());
                return;
            }
        }
        order.setConfirmCount(confirmCount);
        order.setUpdatedAt(LocalDateTime.now());
        depositOrderMapper.updateById(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markConfirmedOrderReorged(Long orderId) {
        DepositOrder order = depositOrderMapper.selectById(orderId);
        if (order == null || order.getStatus() != STATUS_CONFIRMED) {
            return;
        }
        SupportedAsset asset = supportedAssetService.getRequiredById(order.getAssetId());
        assetService.reverseDeposit(order.getUserId(), asset, order.getId(), order.getTxHash());
        order.setStatus(STATUS_REORGED);
        order.setUpdatedAt(LocalDateTime.now());
        depositOrderMapper.updateById(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rewind(BigInteger blockNumber, String blockHash) {
        depositOrderMapper.delete(new LambdaQueryWrapper<DepositOrder>()
                .eq(DepositOrder::getChain, properties.getScan().getChain())
                .eq(DepositOrder::getStatus, STATUS_PENDING)
                .gt(DepositOrder::getBlockNumber, blockNumber));
        ChainBlockScanRecord record = getOrCreateRecord();
        record.setLastScannedBlock(blockNumber);
        record.setLastScannedBlockHash(blockHash);
        record.setConfirmedBlock(blockNumber.min(record.getConfirmedBlock()));
        record.setUpdatedAt(LocalDateTime.now());
        scanRecordMapper.updateById(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateConfirmedBlock(BigInteger confirmedBlock) {
        ChainBlockScanRecord record = getOrCreateRecord();
        record.setConfirmedBlock(confirmedBlock.max(BigInteger.ZERO));
        record.setUpdatedAt(LocalDateTime.now());
        scanRecordMapper.updateById(record);
    }

    private void validateDetectedAsset(DetectedDeposit detected, SupportedAsset asset) {
        boolean sameToken = !StringUtils.hasText(asset.getTokenAddress())
                ? !StringUtils.hasText(detected.tokenAddress())
                : asset.getTokenAddress().equalsIgnoreCase(detected.tokenAddress());
        if (!asset.getChain().equals(detected.chain()) || !sameToken) {
            throw new BizException("detected deposit asset metadata does not match registry");
        }
    }
}
