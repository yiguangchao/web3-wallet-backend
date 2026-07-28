package com.example.wallet.module.withdraw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.wallet.infrastructure.signer.SignedTransaction;
import com.example.wallet.infrastructure.signer.TransactionSignRequest;
import com.example.wallet.infrastructure.signer.TransactionSigner;
import com.example.wallet.infrastructure.web3.Eip1559FeeSuggestion;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.withdraw.entity.TransactionOutbox;
import com.example.wallet.module.withdraw.entity.TransactionOutboxStatus;
import com.example.wallet.module.withdraw.entity.WithdrawChainTransaction;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.mapper.TransactionOutboxMapper;
import com.example.wallet.module.withdraw.mapper.WithdrawChainTransactionMapper;
import com.example.wallet.module.withdraw.config.WithdrawChainProperties;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawTransactionPreparationServiceTest {

    private static final String HOT_WALLET = "0x1111111111111111111111111111111111111111";
    private static final String TO = "0x2222222222222222222222222222222222222222";
    private static final String HASH = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String RAW = "0xf86c09843b9aca0082520894222222222222222222222222222222222222222288016345785d8a000080a0";

    @Mock
    private WalletNonceService nonceService;
    @Mock
    private TransactionSigner signer;
    @Mock
    private Web3Service web3Service;
    @Mock
    private WithdrawChainTransactionMapper chainTransactionMapper;
    @Mock
    private TransactionOutboxMapper outboxMapper;

    private WithdrawTransactionPreparationService service;

    @BeforeEach
    void setUp() {
        WithdrawChainProperties properties = new WithdrawChainProperties();
        service = new WithdrawTransactionPreparationService(
                nonceService, signer, web3Service, properties, chainTransactionMapper, outboxMapper);
    }

    @Test
    void shouldPersistSignedRawTransactionAndOutboxTogether() {
        WithdrawOrder order = order();
        SupportedAsset asset = ethAsset();
        when(signer.hotWalletAddress()).thenReturn(HOT_WALLET);
        when(signer.keyId()).thenReturn("withdraw-v1");
        when(nonceService.allocateForWithdrawal(99L, 11155111L, HOT_WALLET, "withdraw-v1"))
                .thenReturn(new NonceAllocation(11155111L, HOT_WALLET, BigInteger.valueOf(9), "withdraw-v1"));
        when(web3Service.getEip1559FeeSuggestion()).thenReturn(new Eip1559FeeSuggestion(
                BigInteger.valueOf(1_000_000_000L), BigInteger.valueOf(1_000_000_000L),
                BigInteger.valueOf(3_000_000_000L)));
        when(web3Service.estimateGas(any())).thenReturn(BigInteger.valueOf(21_000));
        when(web3Service.getNativeBalanceWei(HOT_WALLET))
                .thenReturn(new BigInteger("2000000000000000000"));
        when(signer.sign(any(TransactionSignRequest.class)))
                .thenReturn(new SignedTransaction(RAW, HASH, HOT_WALLET));
        when(chainTransactionMapper.insert(any(WithdrawChainTransaction.class))).thenReturn(1);
        when(outboxMapper.insert(any(TransactionOutbox.class))).thenReturn(1);

        PreparedChainTransaction result = service.prepare(order, asset);

        assertThat(result.txHash()).isEqualTo(HASH);
        ArgumentCaptor<WithdrawChainTransaction> txCaptor =
                ArgumentCaptor.forClass(WithdrawChainTransaction.class);
        verify(chainTransactionMapper).insert(txCaptor.capture());
        WithdrawChainTransaction transaction = txCaptor.getValue();
        assertThat(transaction.getNonce()).isEqualTo(BigInteger.valueOf(9));
        assertThat(transaction.getValueWei()).isEqualTo(new BigInteger("1000000000000000000"));
        assertThat(transaction.getRawTransaction()).isEqualTo(RAW);
        assertThat(transaction.getTxHash()).isEqualTo(HASH);
        assertThat(transaction.getTransactionFormat()).isEqualTo("EIP1559");
        assertThat(transaction.getEstimatedGas()).isEqualTo(BigInteger.valueOf(21_000));
        assertThat(transaction.getGasLimit()).isEqualTo(BigInteger.valueOf(25_200));
        assertThat(transaction.getMaxFeePerGas()).isEqualTo(BigInteger.valueOf(3_000_000_000L));

        ArgumentCaptor<TransactionOutbox> outboxCaptor = ArgumentCaptor.forClass(TransactionOutbox.class);
        verify(outboxMapper).insert(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getChainTransactionId()).isEqualTo(transaction.getId());
        assertThat(outboxCaptor.getValue().getStatus()).isEqualTo(TransactionOutboxStatus.PENDING.getCode());
        assertThat(outboxCaptor.getValue().getAttemptCount()).isZero();
    }

    @Test
    void shouldReturnExistingTransactionWithoutAllocatingOrSigningAgain() {
        WithdrawChainTransaction existing = new WithdrawChainTransaction();
        existing.setId(700L);
        existing.setTxHash(HASH);
        when(chainTransactionMapper.selectByOrderId(99L)).thenReturn(existing);

        assertThat(service.prepare(order(), ethAsset()))
                .isEqualTo(new PreparedChainTransaction(700L, HASH));

        verifyNoInteractions(nonceService, signer, web3Service, outboxMapper);
        verify(chainTransactionMapper, never()).insert(any(WithdrawChainTransaction.class));
    }

    @Test
    void shouldRejectTransactionWhoseMaximumGasFeeExceedsCap() {
        arrangeNonce();
        when(web3Service.getEip1559FeeSuggestion()).thenReturn(new Eip1559FeeSuggestion(
                BigInteger.valueOf(500_000_000_000L), BigInteger.valueOf(1_000_000_000L),
                BigInteger.valueOf(1_000_000_000_000L)));
        when(web3Service.estimateGas(any())).thenReturn(BigInteger.valueOf(21_000));

        assertThatThrownBy(() -> service.prepare(order(), ethAsset()))
                .hasMessage("maximum transaction fee exceeds configured cap");

        verify(web3Service, never()).getNativeBalanceWei(any());
        verify(signer, never()).sign(any());
    }

    @Test
    void shouldRejectNativeWithdrawalWhenHotWalletCannotCoverValueAndGas() {
        arrangeNonce();
        arrangeNormalFees();
        when(web3Service.getNativeBalanceWei(HOT_WALLET))
                .thenReturn(new BigInteger("1000000000000000000"));

        assertThatThrownBy(() -> service.prepare(order(), ethAsset()))
                .hasMessage("hot wallet ETH balance is insufficient for withdrawal and gas");

        verify(signer, never()).sign(any());
    }

    @Test
    void shouldRejectErc20WithdrawalWhenHotWalletTokenBalanceIsInsufficient() {
        SupportedAsset asset = erc20Asset();
        arrangeNonce();
        arrangeNormalFees();
        when(web3Service.getNativeBalanceWei(HOT_WALLET))
                .thenReturn(new BigInteger("1000000000000000000"));
        when(web3Service.getErc20BalanceRaw(HOT_WALLET, asset.getTokenAddress()))
                .thenReturn(BigInteger.valueOf(999_999));

        assertThatThrownBy(() -> service.prepare(order(), asset))
                .hasMessage("hot wallet token balance is insufficient for withdrawal");

        verify(signer, never()).sign(any());
    }

    private void arrangeNonce() {
        when(signer.hotWalletAddress()).thenReturn(HOT_WALLET);
        when(signer.keyId()).thenReturn("withdraw-v1");
        when(nonceService.allocateForWithdrawal(99L, 11155111L, HOT_WALLET, "withdraw-v1"))
                .thenReturn(new NonceAllocation(11155111L, HOT_WALLET, BigInteger.valueOf(9), "withdraw-v1"));
    }

    private void arrangeNormalFees() {
        when(web3Service.getEip1559FeeSuggestion()).thenReturn(new Eip1559FeeSuggestion(
                BigInteger.valueOf(1_000_000_000L), BigInteger.valueOf(1_000_000_000L),
                BigInteger.valueOf(3_000_000_000L)));
        when(web3Service.estimateGas(any())).thenReturn(BigInteger.valueOf(21_000));
    }

    private WithdrawOrder order() {
        WithdrawOrder order = new WithdrawOrder();
        order.setId(99L);
        order.setChainId(11155111L);
        order.setStatus(WithdrawStatus.SIGNING.getCode());
        order.setToAddress(TO);
        order.setAmount(BigDecimal.ONE);
        return order;
    }

    private SupportedAsset ethAsset() {
        SupportedAsset asset = new SupportedAsset();
        asset.setChainId(11155111L);
        asset.setDecimals(18);
        asset.setTokenAddress(null);
        return asset;
    }

    private SupportedAsset erc20Asset() {
        SupportedAsset asset = new SupportedAsset();
        asset.setChainId(11155111L);
        asset.setDecimals(6);
        asset.setTokenAddress("0x3333333333333333333333333333333333333333");
        return asset;
    }
}
