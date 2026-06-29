package com.example.wallet.infrastructure.web3;

import com.example.wallet.common.exception.BizException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

@Service
public class Web3ServiceImpl implements Web3Service {

    private final Web3j web3j;

    public Web3ServiceImpl(Web3j web3j) {
        this.web3j = web3j;
    }

    @Override
    public boolean isValidAddress(String address) {
        return address != null && address.matches("^0x[0-9a-fA-F]{40}$");
    }

    @Override
    public BigDecimal getEthBalance(String address) {
        if (!isValidAddress(address)) {
            throw new BizException("钱包地址不合法");
        }
        try {
            BigInteger wei = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
            return Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER);
        } catch (Exception ex) {
            throw new BizException("查询 ETH 余额失败: " + ex.getMessage());
        }
    }

    @Override
    public BigDecimal getErc20Balance(String walletAddress, String tokenAddress, Integer decimals) {
        if (!isValidAddress(walletAddress) || !isValidAddress(tokenAddress)) {
            throw new BizException("钱包地址或 Token 地址不合法");
        }
        if (decimals == null || decimals < 0 || decimals > 36) {
            throw new BizException("Token decimals 不合法");
        }
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
            java.util.List<Type> values = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
            BigInteger raw = values.isEmpty() ? BigInteger.ZERO : (BigInteger) values.get(0).getValue();
            return new BigDecimal(raw).divide(BigDecimal.TEN.pow(decimals));
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("查询 ERC-20 余额失败: " + ex.getMessage());
        }
    }

    @Override
    public BigInteger getCurrentBlockNumber() {
        try {
            return web3j.ethBlockNumber().send().getBlockNumber();
        } catch (Exception ex) {
            throw new BizException("查询当前区块失败: " + ex.getMessage());
        }
    }

    @Override
    public TransactionReceipt getTransactionReceipt(String txHash) {
        if (txHash == null || !Numeric.containsHexPrefix(txHash)) {
            throw new BizException("交易哈希不合法");
        }
        try {
            return web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt().orElse(null);
        } catch (Exception ex) {
            throw new BizException("查询交易回执失败: " + ex.getMessage());
        }
    }
}
