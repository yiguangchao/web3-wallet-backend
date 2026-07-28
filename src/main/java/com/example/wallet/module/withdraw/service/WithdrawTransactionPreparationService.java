package com.example.wallet.module.withdraw.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.signer.SignedTransaction;
import com.example.wallet.infrastructure.signer.TransactionSignRequest;
import com.example.wallet.infrastructure.signer.TransactionSigner;
import com.example.wallet.infrastructure.web3.Web3Properties;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.withdraw.entity.TransactionOutbox;
import com.example.wallet.module.withdraw.entity.TransactionOutboxStatus;
import com.example.wallet.module.withdraw.entity.WithdrawChainTransaction;
import com.example.wallet.module.withdraw.entity.WithdrawChainTransactionStatus;
import com.example.wallet.module.withdraw.entity.WithdrawOrder;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.mapper.TransactionOutboxMapper;
import com.example.wallet.module.withdraw.mapper.WithdrawChainTransactionMapper;
import java.math.BigInteger;
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
    private final Web3Properties web3Properties;
    private final WithdrawChainTransactionMapper chainTransactionMapper;
    private final TransactionOutboxMapper outboxMapper;

    public WithdrawTransactionPreparationService(WalletNonceService walletNonceService,
                                                 TransactionSigner transactionSigner,
                                                 Web3Service web3Service,
                                                 Web3Properties web3Properties,
                                                 WithdrawChainTransactionMapper chainTransactionMapper,
                                                 TransactionOutboxMapper outboxMapper) {
        this.walletNonceService = walletNonceService;
        this.transactionSigner = transactionSigner;
        this.web3Service = web3Service;
        this.web3Properties = web3Properties;
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
        BigInteger gasPrice = web3Service.getGasPrice();
        TransactionSignRequest signRequest = buildSignRequest(order, asset, allocation, gasPrice);
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
        chainTransaction.setToAddress(signRequest.to().toLowerCase(Locale.ROOT));
        chainTransaction.setValueWei(signRequest.value());
        chainTransaction.setTransactionData(normalizeData(signRequest.data()));
        chainTransaction.setGasPrice(signRequest.gasPrice());
        chainTransaction.setGasLimit(signRequest.gasLimit());
        chainTransaction.setRawTransaction(signed.rawTransaction());
        chainTransaction.setTxHash(signed.txHash().toLowerCase(Locale.ROOT));
        chainTransaction.setStatus(WithdrawChainTransactionStatus.SIGNED.getCode());
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

    private TransactionSignRequest buildSignRequest(WithdrawOrder order, SupportedAsset asset,
                                                     NonceAllocation allocation, BigInteger gasPrice) {
        if (gasPrice == null || gasPrice.signum() <= 0) {
            throw new BizException("chain gas price is invalid");
        }
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
            return new TransactionSignRequest(
                    allocation.chainId(), allocation.nonce(), gasPrice,
                    BigInteger.valueOf(web3Properties.getErc20TransferGasLimit()),
                    asset.getTokenAddress(), BigInteger.ZERO, FunctionEncoder.encode(transfer));
        }
        return new TransactionSignRequest(
                allocation.chainId(), allocation.nonce(), gasPrice,
                BigInteger.valueOf(web3Properties.getEthTransferGasLimit()),
                order.getToAddress(), rawAmount, "0x");
    }

    private String normalizeData(String data) {
        return StringUtils.hasText(data) ? data.toLowerCase(Locale.ROOT) : "0x";
    }
}
