package com.example.wallet.module.deposit.scanner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.chain.entity.ChainBlockScanRecord;
import com.example.wallet.module.chain.mapper.ChainBlockScanRecordMapper;
import com.example.wallet.module.chain.entity.ChainScannedBlock;
import com.example.wallet.module.chain.mapper.ChainScannedBlockMapper;
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
    private final ChainScannedBlockMapper scannedBlockMapper;
    private final DepositOrderMapper depositOrderMapper;
    private final AssetService assetService;
    private final SupportedAssetService supportedAssetService;
    private final DepositScanProperties properties;

    public DepositScanPersistenceService(ChainBlockScanRecordMapper scanRecordMapper,
                                         ChainScannedBlockMapper scannedBlockMapper,
                                         DepositOrderMapper depositOrderMapper,
                                         AssetService assetService,
                                         SupportedAssetService supportedAssetService,
                                         DepositScanProperties properties) {
        this.scanRecordMapper = scanRecordMapper;
        this.scannedBlockMapper = scannedBlockMapper;
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
    public void saveBatch(List<DetectedDeposit> deposits, List<ScannedBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new BizException("scanned block checkpoints are required");
        }
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
            order.setRiskStatus(0);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            depositOrderMapper.insert(order);
        }
        for (ScannedBlock scanned : blocks) {
            ChainScannedBlock checkpoint = new ChainScannedBlock();
            checkpoint.setChain(properties.getScan().getChain());
            checkpoint.setBlockNumber(scanned.number());
            checkpoint.setBlockHash(scanned.hash().toLowerCase());
            checkpoint.setParentHash(scanned.parentHash().toLowerCase());
            checkpoint.setScannedAt(now);
            checkpoint.setCreatedAt(now);
            scannedBlockMapper.insert(checkpoint);
        }
        ScannedBlock last = blocks.get(blocks.size() - 1);
        ChainBlockScanRecord record = getOrCreateRecord();
        record.setLastScannedBlock(last.number());
        record.setLastScannedBlockHash(last.hash());
        record.setUpdatedAt(now);
        scanRecordMapper.updateById(record);
    }

    public ChainScannedBlock findScannedBlock(BigInteger blockNumber) {
        return scannedBlockMapper.selectByHeight(properties.getScan().getChain(), blockNumber);
    }

    @Transactional(rollbackFor = Exception.class)
    public void ensureCheckpoint(ScannedBlock scanned) {
        LocalDateTime now = LocalDateTime.now();
        ChainScannedBlock checkpoint = new ChainScannedBlock();
        checkpoint.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        checkpoint.setChain(properties.getScan().getChain());
        checkpoint.setBlockNumber(scanned.number());
        checkpoint.setBlockHash(scanned.hash().toLowerCase());
        checkpoint.setParentHash(scanned.parentHash().toLowerCase());
        checkpoint.setScannedAt(now);
        checkpoint.setCreatedAt(now);
        scannedBlockMapper.insertIfAbsent(checkpoint);
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
        DepositOrder order = depositOrderMapper.selectByIdForUpdate(orderId);
        if (order == null || order.getStatus() != STATUS_CONFIRMED) {
            return;
        }
        SupportedAsset asset = supportedAssetService.getRequiredById(order.getAssetId());
        assetService.freezeDepositReorgRisk(order.getUserId(), asset,
                order.getAmount(), order.getId(), order.getTxHash());
        order.setStatus(STATUS_REORGED);
        order.setRiskStatus(1);
        order.setReorgedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        depositOrderMapper.updateById(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void rewind(BigInteger blockNumber, String blockHash) {
        LocalDateTime now = LocalDateTime.now();
        List<DepositOrder> pending = depositOrderMapper.selectList(new LambdaQueryWrapper<DepositOrder>()
                .eq(DepositOrder::getChain, properties.getScan().getChain())
                .eq(DepositOrder::getStatus, STATUS_PENDING)
                .gt(DepositOrder::getBlockNumber, blockNumber));
        for (DepositOrder order : pending) {
            order.setStatus(STATUS_REORGED);
            order.setReorgedAt(now);
            order.setUpdatedAt(now);
            depositOrderMapper.updateById(order);
        }
        scannedBlockMapper.delete(new LambdaQueryWrapper<ChainScannedBlock>()
                .eq(ChainScannedBlock::getChain, properties.getScan().getChain())
                .gt(ChainScannedBlock::getBlockNumber, blockNumber));
        ChainBlockScanRecord record = getOrCreateRecord();
        record.setLastScannedBlock(blockNumber);
        record.setLastScannedBlockHash(blockHash);
        record.setConfirmedBlock(blockNumber.min(record.getConfirmedBlock()));
        record.setUpdatedAt(now);
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
