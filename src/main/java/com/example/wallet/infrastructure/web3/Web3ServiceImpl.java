package com.example.wallet.infrastructure.web3;

import com.example.wallet.common.exception.BizException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

@Service
public class Web3ServiceImpl implements Web3Service {

    private final Web3j web3j;
    private final Web3Properties properties;

    public Web3ServiceImpl(Web3j web3j, Web3Properties properties) {
        this.web3j = web3j;
        this.properties = properties;
    }

    @Override
    public boolean isValidAddress(String address) {
        return address != null && address.matches("^0x[0-9a-fA-F]{40}$");
    }

    @Override
    public BigDecimal getEthBalance(String address) {
        if (!isValidAddress(address)) {
            throw new BizException("wallet address is invalid");
        }
        try {
            BigInteger wei = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
            return Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER);
        } catch (Exception ex) {
            throw new BizException("query ETH balance failed: " + ex.getMessage());
        }
    }

    @Override
    public BigDecimal getErc20Balance(String walletAddress, String tokenAddress, Integer decimals) {
        if (!isValidAddress(walletAddress) || !isValidAddress(tokenAddress)) {
            throw new BizException("wallet address or token address is invalid");
        }
        validateDecimals(decimals);
        try {
            Function function = new Function(
                    "balanceOf",
                    Collections.singletonList(new Address(walletAddress)),
                    Collections.singletonList(new TypeReference<Uint256>() {
                    }));
            String data = FunctionEncoder.encode(function);
            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(walletAddress, tokenAddress, data),
                    DefaultBlockParameterName.LATEST).send();
            if (response.hasError()) {
                throw new BizException(response.getError().getMessage());
            }
            List<Type> values = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            BigInteger raw = values.isEmpty() ? BigInteger.ZERO : (BigInteger) values.get(0).getValue();
            return new BigDecimal(raw).divide(BigDecimal.TEN.pow(decimals));
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("query ERC-20 balance failed: " + ex.getMessage());
        }
    }

    @Override
    public BigInteger getCurrentBlockNumber() {
        try {
            return web3j.ethBlockNumber().send().getBlockNumber();
        } catch (Exception ex) {
            throw new BizException("query current block failed: " + ex.getMessage());
        }
    }

    @Override
    public TransactionReceipt getTransactionReceipt(String txHash) {
        if (txHash == null || !Numeric.containsHexPrefix(txHash)) {
            throw new BizException("transaction hash is invalid");
        }
        try {
            return web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt().orElse(null);
        } catch (Exception ex) {
            throw new BizException("query transaction receipt failed: " + ex.getMessage());
        }
    }

    @Override
    public BigInteger getPendingNonce(String address) {
        if (!isValidAddress(address)) {
            throw new BizException("hot wallet address is invalid");
        }
        try {
            return web3j.ethGetTransactionCount(
                    address, DefaultBlockParameterName.PENDING).send().getTransactionCount();
        } catch (Exception ex) {
            throw new BizException("query hot wallet nonce failed: " + ex.getMessage());
        }
    }

    @Override
    public String broadcastEthTransfer(String toAddress, BigDecimal amount) {
        if (!isValidAddress(toAddress)) {
            throw new BizException("withdraw address is invalid");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("withdraw amount must be greater than zero");
        }
        BigInteger value = Convert.toWei(amount, Convert.Unit.ETHER).toBigIntegerExact();
        RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
                nextNonce(), currentGasPrice(), BigInteger.valueOf(properties.getEthTransferGasLimit()), toAddress, value);
        return signAndSend(rawTransaction);
    }

    @Override
    public String broadcastErc20Transfer(String tokenAddress, String toAddress, BigDecimal amount, Integer decimals) {
        if (!isValidAddress(tokenAddress) || !isValidAddress(toAddress)) {
            throw new BizException("token address or withdraw address is invalid");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("withdraw amount must be greater than zero");
        }
        validateDecimals(decimals);
        BigInteger rawAmount = amount.movePointRight(decimals).toBigIntegerExact();
        Function function = new Function(
                "transfer",
                List.of(new Address(toAddress), new Uint256(rawAmount)),
                Collections.singletonList(new TypeReference<org.web3j.abi.datatypes.Bool>() {
                }));
        RawTransaction rawTransaction = RawTransaction.createTransaction(
                nextNonce(), currentGasPrice(), BigInteger.valueOf(properties.getErc20TransferGasLimit()),
                tokenAddress, BigInteger.ZERO, FunctionEncoder.encode(function));
        return signAndSend(rawTransaction);
    }

    private Credentials withdrawCredentials() {
        String privateKey = properties.getWithdrawPrivateKey();
        if (!StringUtils.hasText(privateKey)) {
            throw new BizException("withdraw private key is not configured");
        }
        privateKey = Numeric.cleanHexPrefix(privateKey.trim());
        try {
            return Credentials.create(privateKey);
        } catch (Exception ex) {
            throw new BizException("withdraw private key is invalid");
        }
    }

    private BigInteger nextNonce() {
        try {
            return web3j.ethGetTransactionCount(
                    withdrawCredentials().getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("query withdraw nonce failed: " + ex.getMessage());
        }
    }

    private BigInteger currentGasPrice() {
        try {
            return web3j.ethGasPrice().send().getGasPrice();
        } catch (Exception ex) {
            throw new BizException("query gas price failed: " + ex.getMessage());
        }
    }

    private String signAndSend(RawTransaction rawTransaction) {
        try {
            byte[] signedMessage = TransactionEncoder.signMessage(
                    rawTransaction, properties.getChainId(), withdrawCredentials());
            EthSendTransaction response = web3j.ethSendRawTransaction(Numeric.toHexString(signedMessage)).send();
            if (response.hasError()) {
                throw new BizException("broadcast transaction failed: " + response.getError().getMessage());
            }
            return response.getTransactionHash();
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("broadcast transaction failed: " + ex.getMessage());
        }
    }

    private void validateDecimals(Integer decimals) {
        if (decimals == null || decimals < 0 || decimals > 36) {
            throw new BizException("token decimals is invalid");
        }
    }
}
