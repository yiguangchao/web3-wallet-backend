package com.example.wallet.infrastructure.signer;

import com.example.wallet.common.exception.BizException;
import java.math.BigInteger;
import java.util.Locale;
import org.springframework.util.StringUtils;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.utils.Numeric;

public final class SignedTransactionVerifier {

    private SignedTransactionVerifier() {
    }

    public static RawTransaction unsignedTransaction(TransactionSignRequest request) {
        validateRequest(request);
        return RawTransaction.createTransaction(
                request.nonce(), request.gasPrice(), request.gasLimit(),
                request.to(), request.value(), normalizeData(request.data()));
    }

    public static SignedTransaction verify(TransactionSignRequest request,
                                           String expectedFromAddress,
                                           String rawTransaction,
                                           String claimedTxHash) {
        validateRequest(request);
        String expectedFrom = normalizeAddress(expectedFromAddress, "hot wallet address is invalid");
        if (!StringUtils.hasText(rawTransaction) || !Numeric.containsHexPrefix(rawTransaction)) {
            throw new BizException("signed raw transaction is invalid");
        }
        try {
            byte[] encoded = Numeric.hexStringToByteArray(rawTransaction);
            String localTxHash = Numeric.toHexString(Hash.sha3(encoded)).toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(claimedTxHash)
                    || !localTxHash.equals(claimedTxHash.toLowerCase(Locale.ROOT))) {
                throw new BizException("signed transaction hash does not match raw transaction");
            }
            RawTransaction decoded = TransactionDecoder.decode(rawTransaction);
            if (!(decoded instanceof SignedRawTransaction signed)) {
                throw new BizException("raw transaction does not contain a signature");
            }
            String from = signed.getFrom().toLowerCase(Locale.ROOT);
            if (!expectedFrom.equals(from)) {
                throw new BizException("signed transaction sender does not match hot wallet");
            }
            Long signedChainId = signed.getChainId();
            if (signedChainId == null || signedChainId != request.chainId()) {
                throw new BizException("signed transaction chain id does not match request");
            }
            requireEqual(decoded.getNonce(), request.nonce(), "nonce");
            requireEqual(decoded.getGasPrice(), request.gasPrice(), "gas price");
            requireEqual(decoded.getGasLimit(), request.gasLimit(), "gas limit");
            requireEqual(decoded.getValue(), request.value(), "value");
            if (!normalizeAddress(decoded.getTo(), "signed transaction recipient is invalid")
                    .equals(normalizeAddress(request.to(), "transaction recipient is invalid"))) {
                throw new BizException("signed transaction recipient does not match request");
            }
            if (!normalizeData(decoded.getData()).equals(normalizeData(request.data()))) {
                throw new BizException("signed transaction data does not match request");
            }
            return new SignedTransaction(Numeric.toHexString(encoded), localTxHash, from);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("signed transaction verification failed: " + ex.getMessage());
        }
    }

    private static void validateRequest(TransactionSignRequest request) {
        if (request == null || request.chainId() <= 0
                || request.nonce() == null || request.nonce().signum() < 0
                || request.gasPrice() == null || request.gasPrice().signum() <= 0
                || request.gasLimit() == null || request.gasLimit().signum() <= 0
                || request.value() == null || request.value().signum() < 0) {
            throw new BizException("transaction signing request is invalid");
        }
        normalizeAddress(request.to(), "transaction recipient is invalid");
        String data = normalizeData(request.data());
        if ((!data.isEmpty() && !data.matches("[0-9a-f]+")) || data.length() % 2 != 0) {
            throw new BizException("transaction data is invalid");
        }
    }

    private static String normalizeAddress(String address, String message) {
        if (!StringUtils.hasText(address) || !address.matches("^0x[0-9a-fA-F]{40}$")) {
            throw new BizException(message);
        }
        return address.toLowerCase(Locale.ROOT);
    }

    private static String normalizeData(String data) {
        return StringUtils.hasText(data)
                ? Numeric.cleanHexPrefix(data).toLowerCase(Locale.ROOT) : "";
    }

    private static void requireEqual(BigInteger actual, BigInteger expected, String field) {
        if (actual == null || !actual.equals(expected)) {
            throw new BizException("signed transaction " + field + " does not match request");
        }
    }
}
