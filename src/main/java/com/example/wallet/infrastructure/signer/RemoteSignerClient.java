package com.example.wallet.infrastructure.signer;

import com.example.wallet.common.exception.BizException;
import java.math.BigInteger;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
@Profile("!dev & !test")
public class RemoteSignerClient implements TransactionSigner {

    private final RestClient restClient;
    private final SignerProperties properties;

    public RemoteSignerClient(RestClient.Builder restClientBuilder, SignerProperties properties) {
        this.restClient = SignerMtls.configure(restClientBuilder, properties).build();
        this.properties = properties;
    }

    @Override
    public String hotWalletAddress() {
        String address = properties.getHotWalletAddress();
        if (!StringUtils.hasText(address) || !address.matches("^0x[0-9a-fA-F]{40}$")) {
            throw new BizException("remote signer hot wallet address is not configured");
        }
        return address.toLowerCase(Locale.ROOT);
    }

    @Override
    public String keyId() {
        if (!StringUtils.hasText(properties.getKeyId())) {
            throw new BizException("remote signer key id is not configured");
        }
        return properties.getKeyId().trim();
    }

    @Override
    public SignedTransaction sign(TransactionSignRequest request) {
        if (!StringUtils.hasText(properties.getRemoteUrl())
                || !properties.getRemoteUrl().startsWith("https://")) {
            throw new BizException("remote signer URL is not configured");
        }
        if (!StringUtils.hasText(properties.getRemoteApiToken())) {
            throw new BizException("remote signer API token is not configured");
        }
        try {
            RemoteSignResponse response = restClient.post()
                    .uri(remoteEndpoint())
                    .header("Authorization", "Bearer " + properties.getRemoteApiToken())
                    .header("Idempotency-Key", keyId() + ":" + request.chainId() + ":" + request.nonce())
                    .body(RemoteSignPayload.from(keyId(), hotWalletAddress(), request))
                    .retrieve()
                    .body(RemoteSignResponse.class);
            if (response == null) {
                throw new BizException("remote signer returned an empty response");
            }
            SignedTransaction verified = SignedTransactionVerifier.verify(
                    request, hotWalletAddress(), response.rawTransaction(), response.txHash());
            if (StringUtils.hasText(response.fromAddress())
                    && !verified.fromAddress().equalsIgnoreCase(response.fromAddress())) {
                throw new BizException("remote signer response sender does not match signature");
            }
            return verified;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("remote transaction signing failed: " + ex.getMessage());
        }
    }

    private String remoteEndpoint() {
        String base = properties.getRemoteUrl().replaceAll("/+$", "");
        String path = StringUtils.hasText(properties.getRemotePath())
                ? properties.getRemotePath().trim() : "/api/v1/sign/ethereum-transaction";
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    public record RemoteSignPayload(
            String keyId,
            String expectedFromAddress,
            String transactionFormat,
            long chainId,
            BigInteger nonce,
            BigInteger gasLimit,
            String to,
            BigInteger value,
            String data,
            BigInteger maxPriorityFeePerGas,
            BigInteger maxFeePerGas,
            java.time.Instant requestedAt) {

        static RemoteSignPayload from(String keyId, String address, TransactionSignRequest request) {
            return new RemoteSignPayload(
                    keyId, address, "EIP1559", request.chainId(), request.nonce(),
                    request.gasLimit(), request.to(), request.value(), request.data(),
                    request.maxPriorityFeePerGas(), request.maxFeePerGas(), java.time.Instant.now());
        }
    }

    public record RemoteSignResponse(String rawTransaction, String txHash, String fromAddress) {
    }
}
