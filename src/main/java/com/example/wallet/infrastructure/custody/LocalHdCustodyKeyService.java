package com.example.wallet.infrastructure.custody;

import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.custody.CustodyWalletProperties.KeyConfig;
import com.example.wallet.infrastructure.web3.Web3Properties;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.MnemonicUtils;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

@Service
@Profile("dev | test")
public class LocalHdCustodyKeyService implements CustodyKeyService {

    private static final int PURPOSE = 44;
    private static final int ETHEREUM_COIN_TYPE = 60;
    private static final long MAX_NORMAL_INDEX = 1L << 31;

    private final Web3j web3j;
    private final Web3Properties web3Properties;
    private final CustodyWalletProperties custodyProperties;

    public LocalHdCustodyKeyService(Web3j web3j,
                                    Web3Properties web3Properties,
                                    CustodyWalletProperties custodyProperties) {
        this.web3j = web3j;
        this.web3Properties = web3Properties;
        this.custodyProperties = custodyProperties;
    }

    @Override
    public DerivedCustodyAddress deriveAddress(String keyVersion, long derivationIndex) {
        KeyConfig key = requireKey(keyVersion);
        Credentials credentials = deriveCredentials(key, derivationIndex);
        return new DerivedCustodyAddress(
                credentials.getAddress().toLowerCase(Locale.ROOT),
                key.getVersion(),
                derivationIndex,
                derivationPath(key, derivationIndex));
    }

    @Override
    public SweepBroadcastResult sweepEth(String keyVersion,
                                         long derivationIndex,
                                         String expectedFromAddress,
                                         String collectionAddress,
                                         BigDecimal minimumAmount,
                                         BigDecimal reserve) {
        Credentials credentials = verifiedCredentials(keyVersion, derivationIndex, expectedFromAddress);
        validateAddress(collectionAddress, "collection address is invalid");
        try {
            BigInteger balance = web3j.ethGetBalance(
                    credentials.getAddress(), DefaultBlockParameterName.PENDING).send().getBalance();
            BigInteger gasPrice = currentGasPrice();
            BigInteger fee = gasPrice.multiply(BigInteger.valueOf(web3Properties.getEthTransferGasLimit()));
            BigInteger reserveWei = Convert.toWei(
                    reserve == null ? BigDecimal.ZERO : reserve, Convert.Unit.ETHER).toBigIntegerExact();
            BigInteger value = balance.subtract(fee).subtract(reserveWei);
            BigInteger minimumWei = Convert.toWei(
                    minimumAmount == null ? BigDecimal.ZERO : minimumAmount, Convert.Unit.ETHER).toBigIntegerExact();
            if (value.signum() <= 0 || value.compareTo(minimumWei) < 0) {
                throw new SweepNotRequiredException("ETH balance is below the sweep threshold after gas");
            }
            RawTransaction transaction = RawTransaction.createEtherTransaction(
                    nextNonce(credentials), gasPrice, BigInteger.valueOf(web3Properties.getEthTransferGasLimit()),
                    collectionAddress, value);
            return new SweepBroadcastResult(
                    signAndSend(transaction, credentials),
                    Convert.fromWei(new BigDecimal(value), Convert.Unit.ETHER));
        } catch (SweepNotRequiredException | BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("sweep ETH failed: " + ex.getMessage());
        }
    }

    @Override
    public SweepBroadcastResult sweepErc20(String keyVersion,
                                           long derivationIndex,
                                           String expectedFromAddress,
                                           String tokenAddress,
                                           Integer decimals,
                                           String collectionAddress) {
        Credentials credentials = verifiedCredentials(keyVersion, derivationIndex, expectedFromAddress);
        validateAddress(tokenAddress, "token address is invalid");
        validateAddress(collectionAddress, "collection address is invalid");
        validateDecimals(decimals);
        try {
            BigInteger rawBalance = erc20Balance(credentials.getAddress(), tokenAddress);
            if (rawBalance.signum() <= 0) {
                throw new SweepNotRequiredException("token balance is zero");
            }
            BigInteger gasPrice = currentGasPrice();
            BigInteger gasCost = gasPrice.multiply(BigInteger.valueOf(web3Properties.getErc20TransferGasLimit()));
            BigInteger ethBalance = web3j.ethGetBalance(
                    credentials.getAddress(), DefaultBlockParameterName.PENDING).send().getBalance();
            if (ethBalance.compareTo(gasCost) < 0) {
                throw new BizException("deposit address has insufficient ETH for ERC-20 sweep gas");
            }
            Function function = new Function(
                    "transfer",
                    List.of(new Address(collectionAddress), new Uint256(rawBalance)),
                    Collections.singletonList(new TypeReference<org.web3j.abi.datatypes.Bool>() {
                    }));
            RawTransaction transaction = RawTransaction.createTransaction(
                    nextNonce(credentials), gasPrice, BigInteger.valueOf(web3Properties.getErc20TransferGasLimit()),
                    tokenAddress, BigInteger.ZERO, FunctionEncoder.encode(function));
            return new SweepBroadcastResult(
                    signAndSend(transaction, credentials),
                    new BigDecimal(rawBalance).movePointLeft(decimals));
        } catch (SweepNotRequiredException | BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("sweep ERC-20 failed: " + ex.getMessage());
        }
    }

    private Credentials verifiedCredentials(String keyVersion, long index, String expectedAddress) {
        Credentials credentials = deriveCredentials(requireKey(keyVersion), index);
        if (!credentials.getAddress().equalsIgnoreCase(expectedAddress)) {
            throw new BizException("derived custody address does not match the stored address");
        }
        return credentials;
    }

    private Credentials deriveCredentials(KeyConfig key, long index) {
        if (index < 0 || index >= MAX_NORMAL_INDEX) {
            throw new BizException("derivation index is out of range");
        }
        byte[] seed = MnemonicUtils.generateSeed(key.getMnemonic().trim(), key.getPassphrase());
        try {
            Bip32ECKeyPair master = Bip32ECKeyPair.generateKeyPair(seed);
            int[] path = {
                    PURPOSE | Bip32ECKeyPair.HARDENED_BIT,
                    ETHEREUM_COIN_TYPE | Bip32ECKeyPair.HARDENED_BIT,
                    key.getAccount() | Bip32ECKeyPair.HARDENED_BIT,
                    0,
                    Math.toIntExact(index)
            };
            return Credentials.create(Bip32ECKeyPair.deriveKeyPair(master, path));
        } finally {
            Arrays.fill(seed, (byte) 0);
        }
    }

    private KeyConfig requireKey(String keyVersion) {
        if (!custodyProperties.isEnabled()) {
            throw new BizException("custody wallet is disabled");
        }
        if (!StringUtils.hasText(keyVersion)) {
            throw new BizException("custody key version is not configured");
        }
        KeyConfig key = custodyProperties.getKeys().stream()
                .filter(candidate -> StringUtils.hasText(candidate.getVersion())
                        && candidate.getVersion().equalsIgnoreCase(keyVersion))
                .findFirst()
                .orElseThrow(() -> new BizException("custody key version is not configured"));
        if (!StringUtils.hasText(key.getMnemonic()) || !MnemonicUtils.validateMnemonic(key.getMnemonic().trim())) {
            throw new BizException("custody mnemonic is missing or invalid");
        }
        if (key.getAccount() < 0) {
            throw new BizException("custody account index is invalid");
        }
        return key;
    }

    private String derivationPath(KeyConfig key, long index) {
        return "m/44'/60'/" + key.getAccount() + "'/0/" + index;
    }

    private BigInteger erc20Balance(String owner, String tokenAddress) throws Exception {
        Function function = new Function(
                "balanceOf",
                Collections.singletonList(new Address(owner)),
                Collections.singletonList(new TypeReference<Uint256>() {
                }));
        EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(owner, tokenAddress, FunctionEncoder.encode(function)),
                DefaultBlockParameterName.PENDING).send();
        if (response.hasError()) {
            throw new BizException(response.getError().getMessage());
        }
        List<Type> values = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        return values.isEmpty() ? BigInteger.ZERO : (BigInteger) values.get(0).getValue();
    }

    private BigInteger nextNonce(Credentials credentials) throws Exception {
        return web3j.ethGetTransactionCount(
                credentials.getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount();
    }

    private BigInteger currentGasPrice() throws Exception {
        return web3j.ethGasPrice().send().getGasPrice();
    }

    private String signAndSend(RawTransaction transaction, Credentials credentials) throws Exception {
        byte[] signed = TransactionEncoder.signMessage(transaction, web3Properties.getChainId(), credentials);
        EthSendTransaction response = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
        if (response.hasError()) {
            throw new BizException("broadcast sweep transaction failed: " + response.getError().getMessage());
        }
        return response.getTransactionHash();
    }

    private void validateAddress(String address, String message) {
        if (address == null || !address.matches("^0x[0-9a-fA-F]{40}$")) {
            throw new BizException(message);
        }
    }

    private void validateDecimals(Integer decimals) {
        if (decimals == null || decimals < 0 || decimals > 36) {
            throw new BizException("token decimals is invalid");
        }
    }
}
