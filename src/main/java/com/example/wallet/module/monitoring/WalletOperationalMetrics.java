package com.example.wallet.module.monitoring;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.infrastructure.signer.TransactionSigner;
import com.example.wallet.infrastructure.web3.Web3Properties;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.mapper.AssetAccountMapper;
import com.example.wallet.module.asset.mapper.SupportedAssetMapper;
import com.example.wallet.module.chain.entity.ChainBlockScanRecord;
import com.example.wallet.module.chain.mapper.ChainBlockScanRecordMapper;
import com.example.wallet.module.deposit.config.DepositScanProperties;
import com.example.wallet.module.monitoring.config.MonitoringProperties;
import com.example.wallet.module.reconciliation.service.ReconciliationService;
import com.example.wallet.module.withdraw.entity.TransactionOutbox;
import com.example.wallet.module.withdraw.entity.WalletNonce;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.mapper.TransactionOutboxMapper;
import com.example.wallet.module.withdraw.mapper.WalletNonceMapper;
import com.example.wallet.module.withdraw.mapper.WithdrawOrderMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WalletOperationalMetrics {
    private final MonitoringProperties properties;
    private final Web3Service web3Service;
    private final Web3Properties web3Properties;
    private final TransactionSigner transactionSigner;
    private final DepositScanProperties depositScanProperties;
    private final ChainBlockScanRecordMapper scanRecordMapper;
    private final TransactionOutboxMapper outboxMapper;
    private final WithdrawOrderMapper withdrawOrderMapper;
    private final WalletNonceMapper walletNonceMapper;
    private final AssetAccountMapper assetAccountMapper;
    private final SupportedAssetMapper supportedAssetMapper;
    private final ReconciliationService reconciliationService;
    private final AtomicLong scanLag = new AtomicLong();
    private final AtomicLong outboxBacklog = new AtomicLong();
    private final AtomicLong pendingWithdrawals = new AtomicLong();
    private final AtomicLong nonceGap = new AtomicLong();
    private final AtomicLong ledgerAnomalies = new AtomicLong();
    private final AtomicLong reconciliationDifferences = new AtomicLong();
    private final AtomicReference<Double> nativeBalance = new AtomicReference<>(0D);
    private final MultiGauge assetBalances;
    private final Counter collectionErrors;
    private final Counter reorgCounter;

    public WalletOperationalMetrics(MonitoringProperties properties,
                                    Web3Service web3Service,
                                    Web3Properties web3Properties,
                                    TransactionSigner transactionSigner,
                                    DepositScanProperties depositScanProperties,
                                    ChainBlockScanRecordMapper scanRecordMapper,
                                    TransactionOutboxMapper outboxMapper,
                                    WithdrawOrderMapper withdrawOrderMapper,
                                    WalletNonceMapper walletNonceMapper,
                                    AssetAccountMapper assetAccountMapper,
                                    SupportedAssetMapper supportedAssetMapper,
                                    ReconciliationService reconciliationService,
                                    MeterRegistry registry) {
        this.properties = properties;
        this.web3Service = web3Service;
        this.web3Properties = web3Properties;
        this.transactionSigner = transactionSigner;
        this.depositScanProperties = depositScanProperties;
        this.scanRecordMapper = scanRecordMapper;
        this.outboxMapper = outboxMapper;
        this.withdrawOrderMapper = withdrawOrderMapper;
        this.walletNonceMapper = walletNonceMapper;
        this.assetAccountMapper = assetAccountMapper;
        this.supportedAssetMapper = supportedAssetMapper;
        this.reconciliationService = reconciliationService;
        Gauge.builder("wallet.scan.block.lag", scanLag, AtomicLong::get).register(registry);
        Gauge.builder("wallet.outbox.backlog", outboxBacklog, AtomicLong::get).register(registry);
        Gauge.builder("wallet.withdraw.pending", pendingWithdrawals, AtomicLong::get).register(registry);
        Gauge.builder("wallet.nonce.gap", nonceGap, AtomicLong::get).register(registry);
        Gauge.builder("wallet.ledger.anomalies", ledgerAnomalies, AtomicLong::get).register(registry);
        Gauge.builder("wallet.reconciliation.differences", reconciliationDifferences, AtomicLong::get)
                .register(registry);
        Gauge.builder("wallet.hot_wallet.native.balance", nativeBalance, AtomicReference::get)
                .baseUnit("ETH").register(registry);
        Gauge.builder("wallet.hot_wallet.gas.balance", nativeBalance, AtomicReference::get)
                .baseUnit("ETH").register(registry);
        this.assetBalances = MultiGauge.builder("wallet.hot_wallet.asset.balance").register(registry);
        this.collectionErrors = registry.counter("wallet.monitoring.collection.errors");
        this.reorgCounter = registry.counter("wallet.chain.reorganizations");
    }

    @Scheduled(fixedDelayString = "${wallet.monitoring.fixed-delay:30000}")
    public void collect() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            String hotWallet = transactionSigner.hotWalletAddress().toLowerCase(Locale.ROOT);
            BigInteger currentBlock = web3Service.getCurrentBlockNumber();
            ChainBlockScanRecord scan = scanRecordMapper.selectOne(
                    new LambdaQueryWrapper<ChainBlockScanRecord>()
                            .eq(ChainBlockScanRecord::getChain,
                                    depositScanProperties.getScan().getChain()));
            scanLag.set(scan == null ? currentBlock.longValue()
                    : currentBlock.subtract(scan.getLastScannedBlock()).max(BigInteger.ZERO).longValue());
            outboxBacklog.set(outboxMapper.selectCount(new LambdaQueryWrapper<TransactionOutbox>()
                    .in(TransactionOutbox::getStatus, 0, 1)));
            pendingWithdrawals.set(withdrawOrderMapper.selectCount(new LambdaQueryWrapper<WithdrawOrder>()
                    .notIn(WithdrawOrder::getStatus,
                            WithdrawStatus.CONFIRMED.getCode(), WithdrawStatus.MANUAL_REVIEW.getCode(),
                            WithdrawStatus.REJECTED.getCode())));
            WalletNonce nonce = walletNonceMapper.selectOne(new LambdaQueryWrapper<WalletNonce>()
                    .eq(WalletNonce::getChainId, web3Properties.getChainId())
                    .eq(WalletNonce::getHotWalletAddress, hotWallet));
            BigInteger chainNonce = web3Service.getPendingNonce(hotWallet);
            nonceGap.set(nonce == null ? 0L
                    : nonce.getNextNonce().subtract(chainNonce).max(BigInteger.ZERO).longValue());
            ledgerAnomalies.set(assetAccountMapper.countInvariantViolations());
            reconciliationDifferences.set(reconciliationService.countOpenDifferences());
            BigInteger nativeWei = web3Service.getNativeBalanceWei(hotWallet);
            nativeBalance.set(new BigDecimal(nativeWei).movePointLeft(18).doubleValue());
            updateAssetBalances(hotWallet, nativeWei);
        } catch (RuntimeException ex) {
            collectionErrors.increment();
        }
    }

    public void recordReorganization() {
        reorgCounter.increment();
    }

    private void updateAssetBalances(String hotWallet, BigInteger nativeWei) {
        List<SupportedAsset> assets = supportedAssetMapper.selectList(
                new LambdaQueryWrapper<SupportedAsset>().eq(SupportedAsset::getStatus, 1));
        List<MultiGauge.Row<?>> rows = new ArrayList<>();
        for (SupportedAsset asset : assets) {
            BigInteger raw = StringUtils.hasText(asset.getTokenAddress())
                    ? web3Service.getErc20BalanceRaw(hotWallet, asset.getTokenAddress()) : nativeWei;
            double value = new BigDecimal(raw).movePointLeft(asset.getDecimals()).doubleValue();
            rows.add(MultiGauge.Row.of(Tags.of("asset", asset.getAssetCode()), value));
        }
        assetBalances.register(rows, true);
    }
}
