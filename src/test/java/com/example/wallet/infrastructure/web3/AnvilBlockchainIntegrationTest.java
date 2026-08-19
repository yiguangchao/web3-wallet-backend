package com.example.wallet.infrastructure.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnvilBlockchainIntegrationTest {

    private static final long DEFAULT_CHAIN_ID = 31_337L;
    private static final String ANVIL_PRIVATE_KEY =
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String RECIPIENT = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";
    private static final BigInteger ETH_AMOUNT = BigInteger.valueOf(100_000_000_000_000_000L);
    private static final BigInteger TOKEN_AMOUNT = BigInteger.valueOf(12_345L);

    private Web3j web3j;
    private Web3Service web3Service;
    private Credentials credentials;
    private long chainId;

    @BeforeAll
    void connectToAnvil() throws Exception {
        String rpcUrl = System.getenv("EVM_RPC_URL");
        boolean ci = "true".equalsIgnoreCase(System.getenv("CI"));
        if (ci) {
            assertThat(rpcUrl).as("CI must configure EVM_RPC_URL").isNotBlank();
        } else {
            assumeTrue(rpcUrl != null && !rpcUrl.isBlank(),
                    "EVM_RPC_URL is not configured; local Anvil tests are optional");
        }

        chainId = Long.parseLong(System.getenv().getOrDefault("EVM_CHAIN_ID", String.valueOf(DEFAULT_CHAIN_ID)));
        web3j = Web3j.build(new HttpService(rpcUrl));
        assertThat(web3j.ethChainId().send().getChainId()).isEqualTo(BigInteger.valueOf(chainId));
        credentials = Credentials.create(ANVIL_PRIVATE_KEY);
        web3Service = new Web3ServiceImpl(web3j,
                new RpcQuorumVerifier(false, null, new SimpleMeterRegistry()));
    }

    @AfterAll
    void closeWeb3j() {
        if (web3j != null) {
            web3j.shutdown();
        }
    }

    @Test
    @Order(1)
    void shouldTransferEthAndReachConfirmations() throws Exception {
        BigInteger recipientBefore = web3j.ethGetBalance(
                RECIPIENT, DefaultBlockParameterName.LATEST).send().getBalance();
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        BigInteger nonce = web3j.ethGetTransactionCount(
                credentials.getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount();
        RawTransaction transaction = RawTransaction.createEtherTransaction(
                nonce, gasPrice, BigInteger.valueOf(21_000L), RECIPIENT, ETH_AMOUNT);

        String txHash = broadcast(transaction);
        TransactionReceipt receipt = awaitReceipt(txHash);

        assertThat(receipt.isStatusOK()).isTrue();
        assertThat(receipt.getTransactionHash()).isEqualToIgnoringCase(txHash);
        assertThat(web3Service.getTransactionReceipt(txHash)).isNotNull();
        assertThat(web3j.ethGetBalance(RECIPIENT, DefaultBlockParameterName.LATEST)
                .send().getBalance().subtract(recipientBefore)).isEqualTo(ETH_AMOUNT);
        assertThat(awaitConfirmations(receipt, 2)).isGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(2)
    void shouldDeployAndTransferErc20() throws Exception {
        String bytecode = loadTokenBytecode();
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        BigInteger deploymentNonce = pendingNonce();
        RawTransaction deployment = RawTransaction.createContractTransaction(
                deploymentNonce, gasPrice, BigInteger.valueOf(2_000_000L), BigInteger.ZERO, bytecode);
        TransactionReceipt deploymentReceipt = awaitReceipt(broadcast(deployment));
        assertThat(deploymentReceipt.isStatusOK()).isTrue();
        assertThat(deploymentReceipt.getContractAddress()).matches("^0x[0-9a-fA-F]{40}$");

        String tokenAddress = deploymentReceipt.getContractAddress();
        Function transfer = new Function(
                "transfer", List.of(new Address(RECIPIENT), new Uint256(TOKEN_AMOUNT)), List.of());
        String data = FunctionEncoder.encode(transfer);
        BigInteger estimatedGas = web3Service.estimateGas(new EvmTransactionRequest(
                credentials.getAddress(), tokenAddress, BigInteger.ZERO, data));
        RawTransaction tokenTransfer = RawTransaction.createTransaction(
                pendingNonce(), gasPrice, estimatedGas.multiply(BigInteger.valueOf(12L)).divide(BigInteger.TEN),
                tokenAddress, BigInteger.ZERO, data);

        String txHash = broadcast(tokenTransfer);
        TransactionReceipt receipt = awaitReceipt(txHash);

        assertThat(receipt.isStatusOK()).isTrue();
        assertThat(web3Service.getErc20BalanceRaw(RECIPIENT, tokenAddress)).isEqualTo(TOKEN_AMOUNT);
        assertThat(receipt.getLogs()).anySatisfy(log -> assertThat(log.getTopics().get(0))
                .isEqualToIgnoringCase(Hash.sha3String("Transfer(address,address,uint256)")));
        assertThat(awaitConfirmations(receipt, 2)).isGreaterThanOrEqualTo(2);
    }

    private String loadTokenBytecode() throws Exception {
        String artifactPath = System.getenv("EVM_ERC20_ARTIFACT");
        boolean ci = "true".equalsIgnoreCase(System.getenv("CI"));
        if (ci) {
            assertThat(artifactPath).as("CI must configure EVM_ERC20_ARTIFACT").isNotBlank();
        } else {
            assumeTrue(artifactPath != null && !artifactPath.isBlank(),
                    "EVM_ERC20_ARTIFACT is not configured; local Anvil tests are optional");
        }
        JsonNode artifact = new ObjectMapper().readTree(Files.readString(Path.of(artifactPath)));
        String bytecode = artifact.path("bytecode").path("object").asText();
        assertThat(bytecode).as("compiled TestToken bytecode").startsWith("0x").hasSizeGreaterThan(2);
        return bytecode;
    }

    private BigInteger pendingNonce() throws Exception {
        return web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.PENDING)
                .send().getTransactionCount();
    }

    private String broadcast(RawTransaction transaction) throws Exception {
        String raw = Numeric.toHexString(TransactionEncoder.signMessage(transaction, chainId, credentials));
        var response = web3j.ethSendRawTransaction(raw).send();
        assertThat(response.hasError())
                .as(response.hasError() ? response.getError().getMessage() : "broadcast accepted")
                .isFalse();
        return response.getTransactionHash();
    }

    private TransactionReceipt awaitReceipt(String txHash) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            var receipt = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
            if (receipt.isPresent()) {
                return receipt.orElseThrow();
            }
            Thread.sleep(200L);
        }
        throw new AssertionError("transaction receipt was not available: " + txHash);
    }

    private int awaitConfirmations(TransactionReceipt receipt, int required) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            BigInteger latest = web3j.ethBlockNumber().send().getBlockNumber();
            int confirmations = latest.subtract(receipt.getBlockNumber()).add(BigInteger.ONE).intValueExact();
            if (confirmations >= required) {
                return confirmations;
            }
            Thread.sleep(200L);
        }
        throw new AssertionError("transaction did not reach " + required + " confirmations");
    }
}
