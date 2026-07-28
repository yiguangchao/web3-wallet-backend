package com.example.wallet.module.withdraw.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.signer.SignedTransaction;
import com.example.wallet.infrastructure.signer.TransactionSignRequest;
import com.example.wallet.infrastructure.signer.TransactionSigner;
import com.example.wallet.infrastructure.web3.Eip1559FeeSuggestion;
import com.example.wallet.infrastructure.web3.EvmTransactionRequest;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.withdraw.entity.TransactionOutbox;
import com.example.wallet.module.withdraw.entity.TransactionOutboxStatus;
import com.example.wallet.module.withdraw.entity.WithdrawChainTransaction;
import com.example.wallet.module.withdraw.entity.WithdrawChainTransactionStatus;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.config.WithdrawChainProperties;
import com.example.wallet.module.withdraw.mapper.TransactionOutboxMapper;
import com.example.wallet.module.withdraw.mapper.WithdrawChainTransactionMapper;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;

@Service
public class WithdrawTransactionPreparationService {

    private final WalletNonceService walletNonceService;
    private final TransactionSigner transactionSigner;
    private final Web3Service web3Service;
    private final WithdrawChainProperties chainProperties;
    private final WithdrawChainTransactionMapper chainTransactionMapper;
    private final TransactionOutboxMapper outboxMapper;

    public WithdrawTransactionPreparationService(WalletNonceService walletNonceService,
                                                 TransactionSigner transactionSigner,
                                                 Web3Service web3Service,
                                                 WithdrawChainProperties chainProperties,
                                                 WithdrawChainTransactionMapper chainTransactionMapper,
                                                 TransactionOutboxMapper outboxMapper) {
        this.walletNonceService = walletNonceService;
        this.transactionSigner = transactionSigner;
        this.web3Service = web3Service;
        this.chainProperties = chainProperties;
        this.chainTransactionMapper = chainTransactionMapper;
        this.outboxMapper = outboxMapper;
    }

    public WithdrawChainTransaction findByOrderId(Long orderId) {
        return chainTransactionMapper.selectByOrderId(orderId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PreparedChainTransaction prepare(WithdrawOrder order, SupportedAsset asset) {
        WithdrawChainTransaction existing = chainTransactionMapper.selectByOrderId(order.getId());
        if (existing != null) {
            return new PreparedChainTransaction(existing.getId(), existing.getTxHash());
        }
        if (!Integer.valueOf(WithdrawStatus.SIGNING.getCode()).equals(order.getStatus())) {
            throw new BizException("withdraw order is not in SIGNING state");
        }
        if (asset.getChainId() == null || !asset.getChainId().equals(order.getChainId())) {
            throw new BizException("withdraw asset chain does not match order");
        }

        NonceAllocation allocation = walletNonceService.allocateForWithdrawal(
                order.getId(), asset.getChainId(), transactionSigner.hotWalletAddress(), transactionSigner.keyId());
        UnsignedTransfer transfer = buildTransfer(order, asset);
        Eip1559FeeSuggestion fees = web3Service.getEip1559FeeSuggestion();
        BigInteger estimatedGas = web3Service.estimateGas(new EvmTransactionRequest(
                allocation.hotWalletAddress(), transfer.to(), transfer.value(), transfer.data()));
        BigInteger gasLimit = applyGasSafetyFactor(estimatedGas);
        BigInteger maxTotalFee = gasLimit.multiply(fees.maxFeePerGas());
        validateFeeCap(maxTotalFee);
        validateHotWalletBalances(allocation.hotWalletAddress(), asset, transfer.rawAmount(),
                transfer.value(), maxTotalFee);
        TransactionSignRequest signRequest = new TransactionSignRequest(
                allocation.chainId(), allocation.nonce(), gasLimit,
                transfer.to(), transfer.value(), transfer.data(),
                fees.maxPriorityFeePerGas(), fees.maxFeePerGas());
        SignedTransaction signed = transactionSigner.sign(signRequest);
        LocalDateTime now = LocalDateTime.now();

        WithdrawChainTransaction chainTransaction = new WithdrawChainTransaction();
        chainTransaction.setId(IdWorker.getId());
        chainTransaction.setWithdrawOrderId(order.getId());
        chainTransaction.setChainId(allocation.chainId());
        chainTransaction.setHotWalletAddress(allocation.hotWalletAddress());
        chainTransaction.setNonce(allocation.nonce());
        chainTransaction.setSignerKeyId(allocation.signerKeyId());
        chainTransaction.setTransactionType(StringUtils.hasText(asset.getTokenAddress()) ? "ERC20" : "NATIVE");
        chainTransaction.setTransactionFormat("EIP1559");
        chainTransaction.setToAddress(signRequest.to().toLowerCase(Locale.ROOT));
        chainTransaction.setValueWei(signRequest.value());
        chainTransaction.setTransactionData(normalizeData(signRequest.data()));
        chainTransaction.setEstimatedGas(estimatedGas);
        chainTransaction.setGasPrice(signRequest.maxFeePerGas());
        chainTransaction.setMaxPriorityFeePerGas(signRequest.maxPriorityFeePerGas());
        chainTransaction.setMaxFeePerGas(signRequest.maxFeePerGas());
        chainTransaction.setGasLimit(signRequest.gasLimit());
        chainTransaction.setMaxTotalFeeWei(maxTotalFee);
        chainTransaction.setRawTransaction(signed.rawTransaction());
        chainTransaction.setTxHash(signed.txHash().toLowerCase(Locale.ROOT));
        chainTransaction.setStatus(WithdrawChainTransactionStatus.SIGNED.getCode());
        chainTransaction.setConfirmationCount(0);
        chainTransaction.setCreatedAt(now);
        chainTransaction.setUpdatedAt(now);
        if (chainTransactionMapper.insert(chainTransaction) != 1) {
            throw new BizException("withdraw chain transaction creation failed");
        }

        TransactionOutbox outbox = new TransactionOutbox();
        outbox.setId(IdWorker.getId());
        outbox.setAggregateType("WITHDRAWAL");
        outbox.setAggregateId(order.getId());
        outbox.setChainTransactionId(chainTransaction.getId());
        outbox.setStatus(TransactionOutboxStatus.PENDING.getCode());
        outbox.setAttemptCount(0);
        outbox.setNextRetryAt(now);
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        if (outboxMapper.insert(outbox) != 1) {
            throw new BizException("withdraw transaction outbox creation failed");
        }
        return new PreparedChainTransaction(chainTransaction.getId(), chainTransaction.getTxHash());
    }

    private UnsignedTransfer buildTransfer(WithdrawOrder order, SupportedAsset asset) {
        BigInteger rawAmount;
        try {
            rawAmount = order.getAmount().movePointRight(asset.getDecimals()).toBigIntegerExact();
        } catch (ArithmeticException ex) {
            throw new BizException("withdraw amount cannot be represented in base units");
        }
        if (StringUtils.hasText(asset.getTokenAddress())) {
            Function transfer = new Function(
                    "transfer",
                    java.util.List.of(new Address(order.getToAddress()), new Uint256(rawAmount)),
                    java.util.List.of(new TypeReference<org.web3j.abi.datatypes.Bool>() { }));
            return new UnsignedTransfer(
                    asset.getTokenAddress(), BigInteger.ZERO, FunctionEncoder.encode(transfer), rawAmount);
        }
        return new UnsignedTransfer(order.getToAddress(), rawAmount, "0x", rawAmount);
    }

    private BigInteger applyGasSafetyFactor(BigInteger estimatedGas) {
        if (estimatedGas == null || estimatedGas.signum() <= 0) {
            throw new BizException("estimated gas is invalid");
        }
        BigInteger gasLimit = new java.math.BigDecimal(estimatedGas)
                .multiply(chainProperties.getGasSafetyMultiplier())
                .setScale(0, RoundingMode.CEILING)
                .toBigIntegerExact();
        if (gasLimit.compareTo(BigInteger.valueOf(chainProperties.getMaxGasLimit())) > 0) {
            throw new BizException("estimated gas exceeds configured gas limit");
        }
        return gasLimit;
    }

    private void validateFeeCap(BigInteger maxTotalFee) {
        if (maxTotalFee.signum() <= 0
                || chainProperties.getMaxTotalFeeWei() == null
                || maxTotalFee.compareTo(chainProperties.getMaxTotalFeeWei()) > 0) {
            throw new BizException("maximum transaction fee exceeds configured cap");
        }
    }

    private void validateHotWalletBalances(String hotWallet, SupportedAsset asset,
                                           BigInteger rawAmount, BigInteger nativeValue,
                                           BigInteger maxTotalFee) {
        BigInteger nativeRequired = nativeValue.add(maxTotalFee);
        if (web3Service.getNativeBalanceWei(hotWallet).compareTo(nativeRequired) < 0) {
            throw new BizException("hot wallet ETH balance is insufficient for withdrawal and gas");
        }
        if (StringUtils.hasText(asset.getTokenAddress())
                && web3Service.getErc20BalanceRaw(hotWallet, asset.getTokenAddress())
                .compareTo(rawAmount) < 0) {
            throw new BizException("hot wallet token balance is insufficient for withdrawal");
        }
    }

    private String normalizeData(String data) {
        return StringUtils.hasText(data) ? data.toLowerCase(Locale.ROOT) : "0x";
    }

    private record UnsignedTransfer(String to, BigInteger value, String data, BigInteger rawAmount) {
    }
}
