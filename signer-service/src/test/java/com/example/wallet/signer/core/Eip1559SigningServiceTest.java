package com.example.wallet.signer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.wallet.signer.api.SignRequest;
import com.example.wallet.signer.config.SignerProperties;
import com.example.wallet.signer.kms.GoogleKmsSigner;
import com.example.wallet.signer.kms.KmsSignature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigInteger;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;

class Eip1559SigningServiceTest {
    @Test
    void signsWithExternallyProducedKmsSignatureAndPersistsIdempotency() throws Exception {
        GoogleKmsSigner kms = mock(GoogleKmsSigner.class);
        SigningPolicyService policy = mock(SigningPolicyService.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        SigningFinalizationService finalization = mock(SigningFinalizationService.class);
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        SignerProperties properties = new SignerProperties();
        properties.setRequestClockSkewSeconds(60);
        ECKeyPair keyPair = Keys.createEcKeyPair();
        String address = "0x" + Keys.getAddress(keyPair.getPublicKey());
        SignRequest request = new SignRequest("withdraw-v1", address, "EIP1559", 1L,
                BigInteger.ZERO, BigInteger.valueOf(21_000),
                "0x1111111111111111111111111111111111111111", BigInteger.TEN, "0x",
                BigInteger.ONE, BigInteger.TWO, Instant.now());
        var keyPolicy = new SigningPolicyService.KeyPolicy("projects/p/locations/l/keyRings/r/cryptoKeys/k/cryptoKeyVersions/1",
                address, 1L, BigInteger.valueOf(100), BigInteger.valueOf(1000), BigInteger.valueOf(100000));
        when(policy.validateAndReserve(request)).thenReturn(keyPolicy);
        RawTransaction raw = RawTransaction.createTransaction(request.chainId(), request.nonce(),
                request.gasLimit(), request.to(), request.value(), request.data(),
                request.maxPriorityFeePerGas(), request.maxFeePerGas());
        byte[] digest = Hash.sha3(TransactionEncoder.encode(raw));
        Sign.SignatureData local = Sign.signMessage(digest, keyPair, false);
        when(kms.sign(keyPolicy.kmsKeyVersionName(), digest)).thenReturn(new KmsSignature(
                new BigInteger(1, local.getR()), new BigInteger(1, local.getS()), keyPair.getPublicKey(), address));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("CN=wallet", null));

        var response = new Eip1559SigningService(kms, policy, idempotency, finalization, json, properties)
                .sign("withdraw-v1:1:000000", request);

        assertThat(response.rawTransaction()).startsWith("0x02");
        assertThat(response.txHash()).matches("^0x[0-9a-f]{64}$");
        assertThat(response.fromAddress()).isEqualToIgnoringCase(address);
        verify(finalization).complete(org.mockito.ArgumentMatchers.eq("withdraw-v1:1:000000"),
                org.mockito.ArgumentMatchers.eq(response), org.mockito.ArgumentMatchers.eq("CN=wallet"),
                org.mockito.ArgumentMatchers.anyMap());
    }
}
