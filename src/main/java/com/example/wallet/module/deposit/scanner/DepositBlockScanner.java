package com.example.wallet.module.deposit.scanner;

import com.example.wallet.infrastructure.redis.RedisDistributedLock;
import com.example.wallet.infrastructure.redis.RedisDistributedLock.LockHandle;
import com.example.wallet.infrastructure.web3.Web3Properties;
import com.example.wallet.infrastructure.web3.RpcBlockHashQuorumVerifier;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.chain.entity.ChainBlockScanRecord;
import com.example.wallet.module.deposit.config.DepositScanProperties;
import com.example.wallet.module.deposit.entity.DepositOrder;
import com.example.wallet.module.wallet.entity.CustodyDepositAddress;
import com.example.wallet.module.wallet.mapper.CustodyDepositAddressMapper;
import com.example.wallet.module.monitoring.WalletOperationalMetrics;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;
import org.web3j.crypto.Hash;

@Component
public class DepositBlockScanner {

    private static final Logger log = LoggerFactory.getLogger(DepositBlockScanner.class);
    private static final BigInteger ETH_LOG_INDEX = BigInteger.valueOf(-1);
    private static final String TRANSFER_TOPIC = Hash.sha3String("Transfer(address,address,uint256)");

    private final Web3j web3j;
    private final CustodyDepositAddressMapper depositAddressMapper;
    private final DepositScanProperties properties;
    private final DepositScanPersistenceService persistenceService;
    private final RedisDistributedLock distributedLock;
    private final SupportedAssetService supportedAssetService;
    private final Web3Properties web3Properties;
    private final WalletOperationalMetrics operationalMetrics;
    private final RpcBlockHashQuorumVerifier blockHashQuorumVerifier;

    public DepositBlockScanner(Web3j web3j,
                               CustodyDepositAddressMapper depositAddressMapper,
                               DepositScanProperties properties,
                               DepositScanPersistenceService persistenceService,
                               RedisDistributedLock distributedLock,
                               SupportedAssetService supportedAssetService,
                               Web3Properties web3Properties,
                               WalletOperationalMetrics operationalMetrics,
                               RpcBlockHashQuorumVerifier blockHashQuorumVerifier) {
        this.web3j = web3j;
        this.depositAddressMapper = depositAddressMapper;
        this.properties = properties;
        this.persistenceService = persistenceService;
        this.distributedLock = distributedLock;
        this.supportedAssetService = supportedAssetService;
        this.web3Properties = web3Properties;
        this.operationalMetrics = operationalMetrics;
        this.blockHashQuorumVerifier = blockHashQuorumVerifier;
    }

    @Scheduled(fixedDelayString = "${wallet.scan.fixed-delay:15000}")
    public void scan() {
        if (!properties.getScan().isEnabled()) {
            return;
        }
        Duration leaseTime = Duration.ofMillis(properties.getScan().getLockLease());
        String lockKey = properties.getScan().getLockKeyPrefix() + properties.getScan().getChain();
        Optional<LockHandle> handle;
        try {
            handle = distributedLock.tryLock(lockKey, leaseTime);
        } catch (Exception ex) {
            log.error("Unable to acquire deposit scan lock {}", lockKey, ex);
            return;
        }
        if (handle.isEmpty()) {
            log.debug("Deposit scan skipped because another instance holds lock {}", lockKey);
            return;
        }
        try {
            scanOnce(handle.get(), leaseTime);
        } catch (Exception ex) {
            log.error("Deposit block scan failed", ex);
        } finally {
            try {
                distributedLock.unlock(handle.get());
            } catch (Exception ex) {
                log.warn("Unable to release deposit scan lock {}", lockKey, ex);
            }
        }
    }

    public void scanOnce() throws Exception {
        scanOnce(null, null);
    }

    private void scanOnce(LockHandle lockHandle, Duration leaseTime) throws Exception {
        ChainBlockScanRecord record = persistenceService.getOrCreateRecord();
        record = handleReorg(record);
        renewLock(lockHandle, leaseTime);
        BigInteger latestBlock = web3j.ethBlockNumber().send().getBlockNumber();
        Map<String, CustodyDepositAddress> wallets = loadWallets();

        BigInteger nextBlock = record.getLastScannedBlock().add(BigInteger.ONE)
                .max(properties.getScan().getInitialBlock());
        while (nextBlock.compareTo(latestBlock) <= 0) {
            BigInteger endBlock = nextBlock
                    .add(BigInteger.valueOf(properties.getScan().getBatchSize() - 1L))
                    .min(latestBlock);
            List<DetectedDeposit> deposits = new ArrayList<>();
            List<ScannedBlock> scannedBlocks = new ArrayList<>();
            for (BigInteger blockNumber = nextBlock;
                 blockNumber.compareTo(endBlock) <= 0;
                 blockNumber = blockNumber.add(BigInteger.ONE)) {
                EthBlock.Block block = getBlock(blockNumber, true);
                collectEthDeposits(block, wallets, deposits);
                scannedBlocks.add(new ScannedBlock(
                        block.getNumber(), block.getHash(), block.getParentHash()));
            }
            collectErc20Deposits(nextBlock, endBlock, wallets, deposits);
            persistenceService.saveBatch(deposits, scannedBlocks);
            renewLock(lockHandle, leaseTime);
            nextBlock = endBlock.add(BigInteger.ONE);
        }

        updateConfirmations(latestBlock);
        renewLock(lockHandle, leaseTime);
        BigInteger confirmedBlock = latestBlock.subtract(BigInteger.valueOf(properties.getConfirmBlocks() - 1L));
        persistenceService.updateConfirmedBlock(confirmedBlock);
    }

    private void renewLock(LockHandle lockHandle, Duration leaseTime) {
        if (lockHandle != null && !distributedLock.renew(lockHandle, leaseTime)) {
            throw new IllegalStateException("Deposit scan lock ownership was lost");
        }
    }

    private ChainBlockScanRecord handleReorg(ChainBlockScanRecord record) throws Exception {
        if (!StringUtils.hasText(record.getLastScannedBlockHash())
                || record.getLastScannedBlock().compareTo(BigInteger.ZERO) < 0) {
            return record;
        }
        EthBlock.Block canonical = getBlock(record.getLastScannedBlock(), false);
        if (record.getLastScannedBlockHash().equalsIgnoreCase(canonical.getHash())) {
            persistenceService.ensureCheckpoint(new ScannedBlock(
                    canonical.getNumber(), canonical.getHash(), canonical.getParentHash()));
            return record;
        }

        ScannedBlock ancestor = findCommonAncestor(record);
        BigInteger rewindBlock = ancestor.number();
        String rewindHash = ancestor.hash();

        for (DepositOrder order : persistenceService.listConfirmedAfter(rewindBlock)) {
            if (!isCanonical(order)) {
                persistenceService.markConfirmedOrderReorged(order.getId());
            }
        }
        persistenceService.rewind(rewindBlock, rewindHash);
        operationalMetrics.recordReorganization();
        log.warn("Chain reorg detected, scanner rewound from block {} to {}",
                record.getLastScannedBlock(), rewindBlock);
        return persistenceService.getOrCreateRecord();
    }

    ScannedBlock findCommonAncestor(ChainBlockScanRecord record) throws Exception {
        BigInteger minimum = properties.getScan().getInitialBlock().subtract(BigInteger.ONE);
        BigInteger searchMinimum = record.getLastScannedBlock()
                .subtract(BigInteger.valueOf(properties.getScan().getReorgDepth())).max(minimum);
        for (BigInteger height = record.getLastScannedBlock(); height.compareTo(searchMinimum) >= 0;
             height = height.subtract(BigInteger.ONE)) {
            var stored = persistenceService.findScannedBlock(height);
            if (stored == null) {
                continue;
            }
            EthBlock.Block candidate = getBlock(height, false);
            if (stored.getBlockHash().equalsIgnoreCase(candidate.getHash())) {
                return new ScannedBlock(height, candidate.getHash(), candidate.getParentHash());
            }
        }
        throw new IllegalStateException("No common ancestor found within configured reorg depth");
    }

    private void updateConfirmations(BigInteger latestBlock) throws Exception {
        for (DepositOrder order : persistenceService.listPendingOrders()) {
            SupportedAsset asset = supportedAssetService.getRequiredById(order.getAssetId());
            int required = asset.getConfirmationBlocks();
            BigInteger confirmations = latestBlock.subtract(order.getBlockNumber()).add(BigInteger.ONE);
            int count = confirmations.max(BigInteger.ZERO)
                    .min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
            boolean canonical = count < required || isCanonical(order);
            persistenceService.updateConfirmation(order.getId(), count, canonical);
        }
    }

    private boolean isCanonical(DepositOrder order) throws Exception {
        EthBlock.Block block = getBlock(order.getBlockNumber(), false);
        return StringUtils.hasText(order.getBlockHash())
                && order.getBlockHash().equalsIgnoreCase(block.getHash());
    }

    private Map<String, CustodyDepositAddress> loadWallets() {
        List<CustodyDepositAddress> addresses = depositAddressMapper
                .selectActivePlatformDepositAddresses(properties.getScan().getChain());
        Map<String, CustodyDepositAddress> result = new HashMap<>();
        for (CustodyDepositAddress address : addresses) {
            result.put(normalize(address.getAddress()), address);
        }
        return result;
    }

    private void collectEthDeposits(EthBlock.Block block,
                                    Map<String, CustodyDepositAddress> wallets,
                                    List<DetectedDeposit> deposits) throws Exception {
        SupportedAsset asset = supportedAssetService.getRequiredNativeAsset(web3Properties.getChainId());
        requireConfiguredChain(asset);
        if (!Boolean.TRUE.equals(asset.getDepositEnabled())) {
            log.debug("Native asset {} is not enabled for deposits", asset.getAssetCode());
            return;
        }
        for (EthBlock.TransactionResult<?> result : block.getTransactions()) {
            EthBlock.TransactionObject transaction = (EthBlock.TransactionObject) result.get();
            if (!StringUtils.hasText(transaction.getTo()) || transaction.getValue().signum() <= 0) {
                continue;
            }
            CustodyDepositAddress wallet = wallets.get(normalize(transaction.getTo()));
            if (wallet == null || !isSuccessful(transaction.getHash())) {
                continue;
            }
            deposits.add(new DetectedDeposit(
                    wallet.getUserId(), asset.getId(), asset.getChain(), asset.getSymbol(), null,
                    transaction.getFrom(), transaction.getTo(),
                    Convert.fromWei(new BigDecimal(transaction.getValue()), Convert.Unit.ETHER),
                    transaction.getHash(), ETH_LOG_INDEX, block.getNumber(), block.getHash()));
        }
    }

    private void collectErc20Deposits(BigInteger fromBlock,
                                      BigInteger toBlock,
                                      Map<String, CustodyDepositAddress> wallets,
                                      List<DetectedDeposit> deposits) throws Exception {
        for (SupportedAsset asset : supportedAssetService
                .listDepositEnabledErc20(web3Properties.getChainId())) {
            requireConfiguredChain(asset);
            EthFilter filter = new EthFilter(
                    DefaultBlockParameter.valueOf(fromBlock),
                    DefaultBlockParameter.valueOf(toBlock),
                    asset.getTokenAddress());
            filter.addSingleTopic(TRANSFER_TOPIC);
            EthLog response = web3j.ethGetLogs(filter).send();
            if (response.hasError()) {
                throw new IllegalStateException(response.getError().getMessage());
            }
            for (EthLog.LogResult<?> result : response.getLogs()) {
                Log event = (Log) result.get();
                if (event.getTopics().size() < 3) {
                    continue;
                }
                String toAddress = topicAddress(event.getTopics().get(2));
                CustodyDepositAddress wallet = wallets.get(normalize(toAddress));
                if (wallet == null) {
                    continue;
                }
                BigInteger rawAmount = Numeric.toBigInt(event.getData());
                if (rawAmount.signum() <= 0) {
                    continue;
                }
                deposits.add(new DetectedDeposit(
                        wallet.getUserId(), asset.getId(), asset.getChain(), asset.getSymbol(),
                        asset.getTokenAddress(), topicAddress(event.getTopics().get(1)), toAddress,
                        new BigDecimal(rawAmount).movePointLeft(asset.getDecimals()),
                        event.getTransactionHash(), event.getLogIndex(), event.getBlockNumber(), event.getBlockHash()));
            }
        }
    }

    private boolean isSuccessful(String txHash) throws Exception {
        return web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt()
                .map(TransactionReceipt::isStatusOK)
                .orElse(false);
    }

    private EthBlock.Block getBlock(BigInteger blockNumber, boolean fullTransactions) throws Exception {
        EthBlock response = web3j.ethGetBlockByNumber(
                DefaultBlockParameter.valueOf(blockNumber), fullTransactions).send();
        if (response.hasError() || response.getBlock() == null) {
            String message = response.hasError() ? response.getError().getMessage() : "block not found";
            throw new IllegalStateException("Unable to read block " + blockNumber + ": " + message);
        }
        blockHashQuorumVerifier.verify(blockNumber, response.getBlock().getHash());
        return response.getBlock();
    }

    private String topicAddress(String topic) {
        return "0x" + Numeric.cleanHexPrefix(topic).substring(24);
    }

    private String normalize(String address) {
        return address.toLowerCase(Locale.ROOT);
    }

    private void requireConfiguredChain(SupportedAsset asset) {
        if (!properties.getScan().getChain().equals(asset.getChain())) {
            throw new IllegalStateException("scan chain does not match supported asset registry");
        }
    }
}
