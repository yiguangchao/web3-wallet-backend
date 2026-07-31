package com.example.wallet.module.reconciliation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.signer.TransactionSigner;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.accounting.mapper.AccountingJournalMapper;
import com.example.wallet.module.reconciliation.config.ReconciliationProperties;
import com.example.wallet.module.reconciliation.entity.ReconciliationDifference;
import com.example.wallet.module.reconciliation.entity.ReconciliationRun;
import com.example.wallet.module.reconciliation.mapper.ReconciliationDifferenceMapper;
import com.example.wallet.module.reconciliation.mapper.ReconciliationProbeMapper;
import com.example.wallet.module.reconciliation.mapper.ReconciliationRunMapper;
import com.example.wallet.module.reconciliation.model.AccountFlowMismatch;
import com.example.wallet.module.reconciliation.model.AssetLiability;
import com.example.wallet.module.reconciliation.model.OrderFlowMismatch;
import com.example.wallet.module.reconciliation.service.ReconciliationService;
import com.example.wallet.module.risk.service.RiskControlService;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReconciliationServiceImpl implements ReconciliationService {
    private final ReconciliationRunMapper runMapper;
    private final ReconciliationDifferenceMapper differenceMapper;
    private final ReconciliationProbeMapper probeMapper;
    private final AccountingJournalMapper accountingJournalMapper;
    private final Web3Service web3Service;
    private final TransactionSigner transactionSigner;
    private final ReconciliationProperties properties;
    private final RiskControlService riskControlService;

    public ReconciliationServiceImpl(ReconciliationRunMapper runMapper,
                                     ReconciliationDifferenceMapper differenceMapper,
                                     ReconciliationProbeMapper probeMapper,
                                     AccountingJournalMapper accountingJournalMapper,
                                     Web3Service web3Service,
                                     TransactionSigner transactionSigner,
                                     ReconciliationProperties properties,
                                     RiskControlService riskControlService) {
        this.runMapper = runMapper;
        this.differenceMapper = differenceMapper;
        this.probeMapper = probeMapper;
        this.accountingJournalMapper = accountingJournalMapper;
        this.web3Service = web3Service;
        this.transactionSigner = transactionSigner;
        this.properties = properties;
        this.riskControlService = riskControlService;
    }

    @Override
    public Long run() {
        LocalDateTime startedAt = LocalDateTime.now();
        ReconciliationRun run = new ReconciliationRun();
        run.setStatus("RUNNING");
        run.setDifferenceCount(0);
        run.setStartedAt(startedAt);
        run.setCreatedAt(startedAt);
        if (runMapper.insert(run) != 1) {
            throw new BizException("reconciliation run creation failed");
        }
        try {
            List<ReconciliationDifference> differences = detectDifferences(run.getId(), startedAt);
            differenceMapper.resolveAllOpen(startedAt);
            for (ReconciliationDifference difference : differences) {
                if (differenceMapper.insert(difference) != 1) {
                    throw new BizException("reconciliation difference creation failed");
                }
            }
            enforceRiskControls(differences, run.getId());
            run.setStatus("COMPLETED");
            run.setDifferenceCount(differences.size());
            run.setFinishedAt(LocalDateTime.now());
            if (runMapper.updateById(run) != 1) {
                throw new BizException("reconciliation run completion failed");
            }
            return run.getId();
        } catch (RuntimeException ex) {
            run.setStatus("FAILED");
            run.setErrorMessage(truncate(ex.getMessage(), 512));
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            riskControlService.pauseWithdrawals(
                    "reconciliation run " + run.getId() + " failed", 0L);
            throw ex;
        }
    }

    @Override
    public List<ReconciliationDifference> listDifferences(String status) {
        LambdaQueryWrapper<ReconciliationDifference> query =
                new LambdaQueryWrapper<ReconciliationDifference>()
                        .eq(StringUtils.hasText(status), ReconciliationDifference::getStatus,
                                StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : null)
                        .orderByDesc(ReconciliationDifference::getDetectedAt);
        return differenceMapper.selectList(query);
    }

    @Override
    public long countOpenDifferences() {
        return differenceMapper.selectCount(new LambdaQueryWrapper<ReconciliationDifference>()
                .eq(ReconciliationDifference::getStatus, "OPEN"));
    }

    private List<ReconciliationDifference> detectDifferences(Long runId, LocalDateTime detectedAt) {
        List<ReconciliationDifference> result = new ArrayList<>();
        for (AccountFlowMismatch mismatch : probeMapper.findAccountFlowMismatches()) {
            BigDecimal expected = mismatch.getExpectedAvailable().add(mismatch.getExpectedFrozen());
            BigDecimal actual = mismatch.getActualAvailable().add(mismatch.getActualFrozen());
            result.add(difference(runId, "ACCOUNT_FLOW", "ACCOUNT_FLOW_BALANCE_MISMATCH",
                    mismatch.getUserId(), mismatch.getAssetId(), mismatch.getAccountId(),
                    expected, actual,
                    "account balance does not match the latest asset flow snapshot", detectedAt));
        }
        for (OrderFlowMismatch mismatch : probeMapper.findOrderFlowMismatches()) {
            result.add(difference(runId, "ORDER_FLOW", mismatch.getDifferenceType(),
                    mismatch.getUserId(), mismatch.getAssetId(), mismatch.getBusinessId(),
                    mismatch.getExpectedAmount(), mismatch.getActualAmount(),
                    "deposit or withdrawal order does not match its required asset flow", detectedAt));
        }
        long imbalancedJournals = accountingJournalMapper.countImbalancedJournals();
        if (imbalancedJournals > 0) {
            result.add(difference(runId, "DOUBLE_ENTRY", "ACCOUNTING_JOURNAL_IMBALANCE",
                    null, null, null, BigDecimal.ZERO, BigDecimal.valueOf(imbalancedJournals),
                    "append-only accounting journals are missing entries or do not sum to zero",
                    detectedAt));
        }
        for (AssetLiability liability : probeMapper.listAssetLiabilities()) {
            BigDecimal onChain = queryPlatformAsset(liability);
            if (onChain.compareTo(liability.getLiabilityAmount()) < 0) {
                result.add(difference(runId, "CHAIN_LIABILITY", "ON_CHAIN_ASSET_SHORTFALL",
                        null, liability.getAssetId(), null, liability.getLiabilityAmount(), onChain,
                        "platform on-chain asset is below internal user liability for "
                                + liability.getAssetCode(), detectedAt));
            }
        }
        return result;
    }

    private BigDecimal queryPlatformAsset(AssetLiability liability) {
        BigInteger totalRaw = BigInteger.ZERO;
        for (String address : platformAddresses()) {
            totalRaw = totalRaw.add(StringUtils.hasText(liability.getTokenAddress())
                    ? web3Service.getErc20BalanceRaw(address, liability.getTokenAddress())
                    : web3Service.getNativeBalanceWei(address));
        }
        return new BigDecimal(totalRaw).movePointLeft(liability.getDecimals());
    }

    private Set<String> platformAddresses() {
        Set<String> addresses = new LinkedHashSet<>();
        for (String configured : properties.getAssetAddresses()) {
            if (StringUtils.hasText(configured)) {
                String normalized = configured.trim().toLowerCase(Locale.ROOT);
                if (!web3Service.isValidAddress(normalized)) {
                    throw new BizException("reconciliation platform address is invalid");
                }
                addresses.add(normalized);
            }
        }
        if (addresses.isEmpty()) {
            addresses.add(transactionSigner.hotWalletAddress().toLowerCase(Locale.ROOT));
        }
        return addresses;
    }

    private void enforceRiskControls(List<ReconciliationDifference> differences, Long runId) {
        if (differences.isEmpty()) {
            return;
        }
        differences.stream().map(ReconciliationDifference::getUserId)
                .filter(java.util.Objects::nonNull).distinct()
                .forEach(userId -> riskControlService.freezeUser(
                        userId, "reconciliation difference detected in run " + runId, 0L));
        riskControlService.pauseWithdrawals(
                "reconciliation differences detected in run " + runId, 0L);
    }

    private ReconciliationDifference difference(Long runId, String layer, String type,
                                                Long userId, Long assetId, Long businessId,
                                                BigDecimal expected, BigDecimal actual,
                                                String detail, LocalDateTime detectedAt) {
        ReconciliationDifference difference = new ReconciliationDifference();
        difference.setRunId(runId);
        difference.setLayerType(layer);
        difference.setDifferenceType(type);
        difference.setSeverity("CRITICAL");
        difference.setUserId(userId);
        difference.setAssetId(assetId);
        difference.setBusinessId(businessId);
        difference.setExpectedAmount(expected);
        difference.setActualAmount(actual);
        difference.setDifferenceAmount(actual == null || expected == null ? null : actual.subtract(expected));
        difference.setDetail(truncate(detail, 512));
        difference.setStatus("OPEN");
        difference.setDetectedAt(detectedAt);
        difference.setCreatedAt(detectedAt);
        return difference;
    }

    private String truncate(String value, int max) {
        String safe = StringUtils.hasText(value) ? value : "unknown reconciliation error";
        return safe.length() <= max ? safe : safe.substring(0, max);
    }
}
