package com.example.wallet.infrastructure.custody;

import com.example.wallet.common.exception.BizException;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
@Profile("!dev & !test")
public class RemoteCustodyKeyService implements CustodyKeyService {
    private final RestClient restClient;
    private final CustodyWalletProperties properties;

    public RemoteCustodyKeyService(RestClient.Builder builder, CustodyWalletProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    @Override
    public DerivedCustodyAddress deriveAddress(String keyVersion, long derivationIndex) {
        DeriveResponse response = post("/api/v1/custody/derive",
                new DeriveRequest(keyVersion, derivationIndex), DeriveResponse.class);
        if (response == null || !validAddress(response.address())
                || !keyVersion.equals(response.keyVersion())
                || derivationIndex != response.derivationIndex()) {
            throw new BizException("remote custody returned an invalid derivation response");
        }
        return new DerivedCustodyAddress(response.address().toLowerCase(Locale.ROOT),
                response.keyVersion(), response.derivationIndex(), response.derivationPath());
    }

    @Override
    public SweepBroadcastResult sweepEth(String keyVersion, long derivationIndex,
                                          String expectedFromAddress, String collectionAddress,
                                          BigDecimal minimumAmount, BigDecimal reserve) {
        return sweep(new SweepRequest("NATIVE", keyVersion, derivationIndex,
                expectedFromAddress, collectionAddress, null, null, minimumAmount, reserve));
    }

    @Override
    public SweepBroadcastResult sweepErc20(String keyVersion, long derivationIndex,
                                            String expectedFromAddress, String tokenAddress,
                                            Integer decimals, String collectionAddress) {
        return sweep(new SweepRequest("ERC20", keyVersion, derivationIndex,
                expectedFromAddress, collectionAddress, tokenAddress, decimals, null, null));
    }

    private SweepBroadcastResult sweep(SweepRequest request) {
        SweepResponse response = post("/api/v1/custody/sweep", request, SweepResponse.class);
        if (response == null || response.amount() == null || response.amount().signum() <= 0
                || response.txHash() == null || !response.txHash().matches("^0x[0-9a-fA-F]{64}$")) {
            throw new BizException("remote custody returned an invalid sweep response");
        }
        return new SweepBroadcastResult(response.txHash().toLowerCase(Locale.ROOT), response.amount());
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        requireConfiguration();
        try {
            return restClient.post().uri(baseUrl() + path)
                    .header("Authorization", "Bearer " + properties.getRemoteApiToken())
                    .body(body).retrieve().body(responseType);
        } catch (HttpClientErrorException.Conflict ex) {
            throw new SweepNotRequiredException("remote custody reports sweep is not required");
        } catch (SweepNotRequiredException | BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("remote custody request failed");
        }
    }

    private void requireConfiguration() {
        if (!StringUtils.hasText(properties.getRemoteUrl())
                || !properties.getRemoteUrl().startsWith("https://")
                || !StringUtils.hasText(properties.getRemoteApiToken())) {
            throw new BizException("secure remote custody service is not configured");
        }
    }

    private String baseUrl() {
        return properties.getRemoteUrl().replaceAll("/+$", "");
    }

    private boolean validAddress(String address) {
        return address != null && address.matches("^0x[0-9a-fA-F]{40}$");
    }

    private record DeriveRequest(String keyVersion, long derivationIndex) {}
    private record DeriveResponse(String address, String keyVersion, long derivationIndex,
                                  String derivationPath) {}
    private record SweepRequest(String assetType, String keyVersion, long derivationIndex,
                                String expectedFromAddress, String collectionAddress,
                                String tokenAddress, Integer decimals,
                                BigDecimal minimumAmount, BigDecimal reserve) {}
    private record SweepResponse(String txHash, BigDecimal amount) {}
}
