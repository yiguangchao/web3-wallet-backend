package com.example.wallet.module.reconciliation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.wallet.infrastructure.signer.TransactionSigner;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.reconciliation.config.ReconciliationProperties;
import com.example.wallet.module.reconciliation.entity.ReconciliationDifference;
import com.example.wallet.module.reconciliation.entity.ReconciliationRun;
import com.example.wallet.module.reconciliation.mapper.ReconciliationDifferenceMapper;
import com.example.wallet.module.reconciliation.mapper.ReconciliationProbeMapper;
import com.example.wallet.module.reconciliation.mapper.ReconciliationRunMapper;
import com.example.wallet.module.reconciliation.model.AccountFlowMismatch;
import com.example.wallet.module.reconciliation.model.AssetLiability;
import com.example.wallet.module.risk.service.RiskControlService;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceImplTest {
    private static final String HOT = "0x1111111111111111111111111111111111111111";
    @Mock private ReconciliationRunMapper runMapper;
    @Mock private ReconciliationDifferenceMapper differenceMapper;
    @Mock private ReconciliationProbeMapper probeMapper;
    @Mock private Web3Service web3Service;
    @Mock private TransactionSigner signer;
    @Mock private RiskControlService riskControlService;
    private ReconciliationServiceImpl service;

    @BeforeEach
    void setUp() {
        when(runMapper.insert(any(ReconciliationRun.class))).thenAnswer(invocation -> {
            ReconciliationRun run = invocation.getArgument(0);
            run.setId(900L);
            return 1;
        });
        when(runMapper.updateById(any(ReconciliationRun.class))).thenReturn(1);
        when(differenceMapper.insert(any(ReconciliationDifference.class))).thenReturn(1);
        service = new ReconciliationServiceImpl(runMapper, differenceMapper, probeMapper,
                web3Service, signer, new ReconciliationProperties(), riskControlService);
    }

    @Test
    void shouldPersistAccountDifferenceAndApplyRiskControls() {
        AccountFlowMismatch mismatch = new AccountFlowMismatch();
        mismatch.setAccountId(10L);
        mismatch.setUserId(1L);
        mismatch.setAssetId(7001L);
        mismatch.setExpectedAvailable(new BigDecimal("5"));
        mismatch.setActualAvailable(new BigDecimal("4"));
        mismatch.setExpectedFrozen(BigDecimal.ZERO);
        mismatch.setActualFrozen(BigDecimal.ZERO);
        when(probeMapper.findAccountFlowMismatches()).thenReturn(List.of(mismatch));
        when(probeMapper.findOrderFlowMismatches()).thenReturn(List.of());
        when(probeMapper.listAssetLiabilities()).thenReturn(List.of());

        assertThat(service.run()).isEqualTo(900L);

        ArgumentCaptor<ReconciliationDifference> captor =
                ArgumentCaptor.forClass(ReconciliationDifference.class);
        verify(differenceMapper).insert(captor.capture());
        assertThat(captor.getValue().getLayerType()).isEqualTo("ACCOUNT_FLOW");
        assertThat(captor.getValue().getDifferenceAmount()).isEqualByComparingTo("-1");
        verify(riskControlService).freezeUser(1L,
                "reconciliation difference detected in run 900", 0L);
        verify(riskControlService).pauseWithdrawals(
                "reconciliation differences detected in run 900", 0L);
    }

    @Test
    void shouldDetectOnChainAssetShortfallAgainstInternalLiability() {
        when(probeMapper.findAccountFlowMismatches()).thenReturn(List.of());
        when(probeMapper.findOrderFlowMismatches()).thenReturn(List.of());
        AssetLiability liability = new AssetLiability();
        liability.setAssetId(7001L);
        liability.setAssetCode("ETH");
        liability.setDecimals(18);
        liability.setLiabilityAmount(new BigDecimal("2"));
        when(probeMapper.listAssetLiabilities()).thenReturn(List.of(liability));
        when(signer.hotWalletAddress()).thenReturn(HOT);
        when(web3Service.getNativeBalanceWei(HOT))
                .thenReturn(new BigInteger("1000000000000000000"));

        service.run();

        ArgumentCaptor<ReconciliationDifference> captor =
                ArgumentCaptor.forClass(ReconciliationDifference.class);
        verify(differenceMapper).insert(captor.capture());
        assertThat(captor.getValue().getDifferenceType()).isEqualTo("ON_CHAIN_ASSET_SHORTFALL");
        assertThat(captor.getValue().getActualAmount()).isEqualByComparingTo("1");
    }
}
