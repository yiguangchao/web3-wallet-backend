package com.example.wallet.infrastructure.signer;

import com.example.wallet.common.exception.BizException;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

@Component
@Profile("!prod & (dev | test)")
public class LocalDevSigner implements TransactionSigner {

    private final SignerProperties properties;

    public LocalDevSigner(SignerProperties properties) {
        this.properties = properties;
    }

    @Override
    public String hotWalletAddress() {
        String derived = credentials().getAddress().toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(properties.getHotWalletAddress())
                && !derived.equalsIgnoreCase(properties.getHotWalletAddress())) {
            throw new BizException("configured hot wallet does not match local signer key");
        }
        return derived;
    }

    @Override
    public String keyId() {
        if (!StringUtils.hasText(properties.getKeyId())) {
            throw new BizException("signer key id is not configured");
        }
        return properties.getKeyId().trim();
    }

    @Override
    public SignedTransaction sign(TransactionSignRequest request) {
        Credentials credentials = credentials();
        byte[] signed = TransactionEncoder.signMessage(
                SignedTransactionVerifier.unsignedTransaction(request), request.chainId(), credentials);
        String rawTransaction = Numeric.toHexString(signed);
        return SignedTransactionVerifier.verify(
                request, hotWalletAddress(), rawTransaction,
                Numeric.toHexString(org.web3j.crypto.Hash.sha3(signed)));
    }

    private Credentials credentials() {
        if (!StringUtils.hasText(properties.getLocalPrivateKey())) {
            throw new BizException("local signer private key is not configured");
        }
        try {
            return Credentials.create(Numeric.cleanHexPrefix(properties.getLocalPrivateKey().trim()));
        } catch (Exception ex) {
            throw new BizException("local signer private key is invalid");
        }
    }
}
