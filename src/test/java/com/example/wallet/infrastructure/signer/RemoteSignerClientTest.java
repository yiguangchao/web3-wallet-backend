package com.example.wallet.infrastructure.signer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.wallet.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.web3j.crypto.Credentials;

class RemoteSignerClientTest {

    private static final String PRIVATE_KEY =
            "0000000000000000000000000000000000000000000000000000000000000001";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TransactionSignRequest request;
    private SignedTransaction localSigned;
    private SignerProperties remoteProperties;
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        request = new TransactionSignRequest(
                11155111L, BigInteger.valueOf(7), BigInteger.valueOf(21_000),
                "0x1111111111111111111111111111111111111111",
                BigInteger.valueOf(123), "0x", BigInteger.valueOf(1_000_000_000L),
                BigInteger.valueOf(3_000_000_000L));
        SignerProperties localProperties = new SignerProperties();
        localProperties.setLocalPrivateKey(PRIVATE_KEY);
        localProperties.setKeyId("withdraw-v1");
        localSigned = new LocalDevSigner(localProperties).sign(request);

        remoteProperties = new SignerProperties();
        remoteProperties.setRemoteUrl("http://signer.internal");
        remoteProperties.setKeyId("withdraw-v1");
        remoteProperties.setHotWalletAddress(Credentials.create(PRIVATE_KEY).getAddress());
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    @Test
    void shouldAcceptRemoteSignatureOnlyAfterLocalVerification() throws Exception {
        server.expect(once(), requestTo("http://signer.internal/api/v1/sign/ethereum-transaction"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(Map.of(
                        "rawTransaction", localSigned.rawTransaction(),
                        "txHash", localSigned.txHash(),
                        "fromAddress", localSigned.fromAddress())), MediaType.APPLICATION_JSON));

        SignedTransaction signed = new RemoteSignerClient(restClientBuilder, remoteProperties).sign(request);

        assertThat(signed).isEqualTo(localSigned);
        server.verify();
    }

    @Test
    void shouldRejectRemoteHashThatDoesNotMatchRawTransaction() throws Exception {
        server.expect(once(), requestTo("http://signer.internal/api/v1/sign/ethereum-transaction"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(Map.of(
                        "rawTransaction", localSigned.rawTransaction(),
                        "txHash", "0x" + "0".repeat(64),
                        "fromAddress", localSigned.fromAddress())), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new RemoteSignerClient(restClientBuilder, remoteProperties).sign(request))
                .isInstanceOf(BizException.class)
                .hasMessage("signed transaction hash does not match raw transaction");
    }
}
