package com.example.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.example.wallet.infrastructure.security.LoginUser;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.deposit.config.DepositScanProperties;
import com.example.wallet.module.deposit.scanner.DepositBlockScanner;
import com.example.wallet.module.reconciliation.service.ReconciliationService;
import com.example.wallet.module.withdraw.dto.WithdrawApplyRequest;
import com.example.wallet.module.withdraw.entity.WithdrawStatus;
import com.example.wallet.module.withdraw.scanner.WithdrawOutboxBroadcaster;
import com.example.wallet.module.withdraw.service.WithdrawOutboxService;
import com.example.wallet.module.withdraw.service.WithdrawService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WalletBusinessE2EIntegrationTest {

    private static final long CHAIN_ID = 31_337L;
    private static final long USER_ID = 81_001L;
    private static final long REORG_USER_ID = 81_002L;
    private static final long REVIEWER_ID = 82_001L;
    private static final long OPERATOR_ID = 82_002L;
    private static final String CHAIN = "ETH_SEPOLIA";
    private static final String HOT_PRIVATE_KEY =
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String HOT_WALLET = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";
    private static final String DEPOSIT_ADDRESS = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final String WITHDRAW_ADDRESS = "0x3c44cdddb6a900fa2b585dd299e03d12fa4293bc";
    private static final String REORG_ADDRESS = "0x90f79bf6eb2c4f870365e785982e1f101e93b906";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withCommand("--log-bin-trust-function-creators=1")
            .withDatabaseName("wallet_e2e")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        // PER_CLASS creates the Spring test instance before Testcontainers' beforeAll callback.
        Startables.deepStart(Stream.of(MYSQL, REDIS)).join();
        String rpcUrl = System.getenv().getOrDefault("EVM_RPC_URL", "http://127.0.0.1:8545");
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("web3.rpc-url", () -> rpcUrl);
        registry.add("web3.chain-id", () -> CHAIN_ID);
        registry.add("web3.max-requests-per-second", () -> 500);
        registry.add("wallet.confirm-blocks", () -> 1);
        registry.add("wallet.scan.enabled", () -> false);
        registry.add("wallet.scan.initial-block", () -> 0);
        registry.add("wallet.scan.batch-size", () -> 500);
        registry.add("wallet.withdraw-broadcast.enabled", () -> true);
        registry.add("wallet.withdraw-broadcast.fixed-delay", () -> 3_600_000);
        registry.add("wallet.withdraw-broadcast.processing-timeout", () -> 1_000);
        registry.add("wallet.withdraw-chain.receipt-fixed-delay", () -> 3_600_000);
        registry.add("wallet.monitoring.enabled", () -> false);
        registry.add("wallet.reconciliation.enabled", () -> false);
        registry.add("wallet.signer.local-private-key", () -> HOT_PRIVATE_KEY);
        registry.add("wallet.signer.hot-wallet-address", () -> HOT_WALLET);
        registry.add("wallet.signer.key-id", () -> "anvil-e2e");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Web3j web3j;

    @Autowired
    private Web3Service web3Service;

    @Autowired
    private DepositBlockScanner depositBlockScanner;

    @Autowired
    private DepositScanProperties depositScanProperties;

    @Autowired
    private WithdrawService withdrawService;

    @Autowired
    private WithdrawOutboxService outboxService;

    @Autowired
    private WithdrawOutboxBroadcaster outboxBroadcaster;

    @Autowired
    private ReconciliationService reconciliationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private Credentials hotWallet;
    private String rpcUrl;
    private String tokenAddress;

    @BeforeAll
    void setUpBusinessEnvironment() throws Exception {
        rpcUrl = System.getenv("EVM_RPC_URL");
        boolean ci = "true".equalsIgnoreCase(System.getenv("CI"));
        if (ci) {
            assertThat(rpcUrl).as("CI must configure EVM_RPC_URL").isNotBlank();
        } else {
            assumeTrue(rpcUrl != null && !rpcUrl.isBlank(),
                    "EVM_RPC_URL is not configured; local business E2E tests are optional");
        }
        assertThat(web3j.ethChainId().send().getChainId()).isEqualTo(BigInteger.valueOf(CHAIN_ID));
        BigInteger isolatedScanStart = web3j.ethBlockNumber().send().getBlockNumber().add(BigInteger.ONE);
        depositScanProperties.getScan().setInitialBlock(isolatedScanStart);
        hotWallet = Credentials.create(HOT_PRIVATE_KEY);
        tokenAddress = deployToken();

        jdbcTemplate.update("UPDATE supported_asset SET chain_id=?, confirmation_blocks=1 WHERE id=7001", CHAIN_ID);
        jdbcTemplate.update("UPDATE supported_asset SET chain_id=?, token_address=?, decimals=18, "
                        + "confirmation_blocks=1, min_withdraw=0.000000000000000001, "
                        + "platform_withdraw_fee=1 WHERE id=7002",
                CHAIN_ID, tokenAddress.toLowerCase());
        jdbcTemplate.update("UPDATE withdraw_risk_policy SET user_daily_limit=1000, "
                + "platform_daily_limit=10000, whitelist_required=1");
        insertUser(USER_ID, "e2e-user", "USER");
        insertUser(REORG_USER_ID, "e2e-reorg-user", "USER");
        insertUser(REVIEWER_ID, "e2e-reviewer", "REVIEWER");
        insertUser(OPERATOR_ID, "e2e-operator", "OPERATOR");
        insertDepositAddress(83_001L, USER_ID, DEPOSIT_ADDRESS, 1L);
        insertDepositAddress(83_002L, REORG_USER_ID, REORG_ADDRESS, 2L);
        jdbcTemplate.update("INSERT INTO withdraw_address_whitelist "
                        + "(id,user_id,chain_id,address,label,status,created_by) VALUES (?,?,?,?,?,1,?)",
                84_001L, USER_ID, CHAIN_ID, WITHDRAW_ADDRESS, "Anvil E2E", OPERATOR_ID);
    }

    @AfterAll
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @Order(1)
    void shouldCreditEthDepositExactlyOnce() throws Exception {
        String txHash = sendEth(DEPOSIT_ADDRESS, new BigDecimal("1"));
        awaitReceipt(txHash);

        depositBlockScanner.scanOnce();
        depositBlockScanner.scanOnce();

        assertThat(count("SELECT COUNT(*) FROM deposit_order WHERE asset_id=7001 AND tx_hash=?", txHash))
                .isEqualTo(1);
        assertThat(amount("SELECT available_balance FROM asset_account WHERE user_id=? AND asset_id=7001", USER_ID))
                .isEqualByComparingTo("1");
        assertThat(count("SELECT COUNT(*) FROM asset_flow WHERE business_type='DEPOSIT' "
                + "AND tx_hash=?", txHash)).isEqualTo(1);
    }

    @Test
    @Order(2)
    void shouldCreditErc20DepositExactlyOnce() throws Exception {
        String txHash = transferToken(DEPOSIT_ADDRESS, new BigInteger("100000000000000000000"));
        awaitReceipt(txHash);

        depositBlockScanner.scanOnce();
        depositBlockScanner.scanOnce();

        assertThat(count("SELECT COUNT(*) FROM deposit_order WHERE asset_id=7002 AND tx_hash=?", txHash))
                .isEqualTo(1);
        assertThat(amount("SELECT available_balance FROM asset_account WHERE user_id=? AND asset_id=7002", USER_ID))
                .isEqualByComparingTo("100");
        assertThat(count("SELECT COUNT(*) FROM asset_flow WHERE business_type='DEPOSIT' "
                + "AND tx_hash=?", txHash)).isEqualTo(1);
    }

    @Test
    @Order(3)
    void shouldCompleteEthWithdrawalEndToEnd() throws Exception {
        BigInteger recipientBefore = web3Service.getNativeBalanceWei(WITHDRAW_ADDRESS);
        Long orderId = prepareWithdrawal("ETH", new BigDecimal("0.1"), "e2e-eth-withdraw");

        String firstHash = withdrawService.broadcastWithdraw(orderId);
        assertThat(withdrawService.broadcastWithdraw(orderId)).isEqualTo(firstHash);
        outboxBroadcaster.runOnce();
        assertThat(web3Service.isTransactionKnown(firstHash))
                .as("ETH withdrawal must be submitted by the outbox worker")
                .isTrue();
        awaitReceipt(firstHash);
        outboxBroadcaster.runOnce();
        authenticate(OPERATOR_ID, "e2e-operator", "OPERATOR");
        assertThat(withdrawService.syncWithdrawStatus(orderId)).isEqualTo(WithdrawStatus.MINED.getCode());
        assertThat(withdrawService.syncWithdrawStatus(orderId)).isEqualTo(WithdrawStatus.CONFIRMED.getCode());

        assertThat(web3Service.getNativeBalanceWei(WITHDRAW_ADDRESS).subtract(recipientBefore))
                .isEqualTo(new BigInteger("100000000000000000"));
        assertThat(count("SELECT attempt_count FROM transaction_outbox WHERE aggregate_id=?", orderId))
                .isEqualTo(1);
        assertBalanceInvariant(USER_ID, 7001L);
    }

    @Test
    @Order(4)
    void shouldCompleteErc20WithdrawalEndToEnd() throws Exception {
        BigInteger recipientBefore = web3Service.getErc20BalanceRaw(WITHDRAW_ADDRESS, tokenAddress);
        Long orderId = prepareWithdrawal("USDC", new BigDecimal("10"), "e2e-token-withdraw");

        String txHash = withdrawService.broadcastWithdraw(orderId);
        outboxBroadcaster.runOnce();
        assertThat(web3Service.isTransactionKnown(txHash))
                .as("ERC-20 withdrawal must be submitted by the outbox worker")
                .isTrue();
        awaitReceipt(txHash);
        authenticate(OPERATOR_ID, "e2e-operator", "OPERATOR");
        assertThat(withdrawService.syncWithdrawStatus(orderId)).isEqualTo(WithdrawStatus.MINED.getCode());
        assertThat(withdrawService.syncWithdrawStatus(orderId)).isEqualTo(WithdrawStatus.CONFIRMED.getCode());

        assertThat(web3Service.getErc20BalanceRaw(WITHDRAW_ADDRESS, tokenAddress).subtract(recipientBefore))
                .isEqualTo(new BigInteger("10000000000000000000"));
        assertBalanceInvariant(USER_ID, 7002L);
    }

    @Test
    @Order(5)
    void shouldSerializeConcurrentWithdrawalsAndRecoverStaleOutbox() {
        int requests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Long>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                int index = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    await(start);
                    return withdrawService.apply(USER_ID,
                            request("ETH", new BigDecimal("0.01"), "e2e-concurrent-" + index));
                }, executor));
            }
            start.countDown();
            assertThat(futures.stream().map(CompletableFuture::join).distinct().count()).isEqualTo(requests);
        } finally {
            executor.shutdownNow();
        }
        assertBalanceInvariant(USER_ID, 7001L);

        Long recoveryOrder = futures.get(0).join();
        authenticate(REVIEWER_ID, "e2e-reviewer", "REVIEWER");
        withdrawService.approveWithdraw(recoveryOrder, "recovery test");
        authenticate(OPERATOR_ID, "e2e-operator", "OPERATOR");
        withdrawService.broadcastWithdraw(recoveryOrder);
        jdbcTemplate.update("UPDATE transaction_outbox SET status=1, attempt_count=attempt_count+1, "
                        + "next_retry_at=NULL, locked_by='dead-worker', "
                        + "locked_at=DATE_SUB(NOW(), INTERVAL 10 MINUTE) WHERE aggregate_id=?",
                recoveryOrder);

        outboxService.recoverStaleProcessing();

        assertThat(count("SELECT status FROM transaction_outbox WHERE aggregate_id=?", recoveryOrder))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_error FROM transaction_outbox WHERE aggregate_id=?", String.class, recoveryOrder))
                .isEqualTo("recovered stale outbox delivery");
    }

    @Test
    @Order(6)
    void shouldPauseWithdrawalsWhenReconciliationFindsAnomaly() {
        jdbcTemplate.update("UPDATE asset_flow SET after_available_balance=after_available_balance+1 "
                + "WHERE id=(SELECT id FROM (SELECT id FROM asset_flow WHERE user_id=? AND asset_id=7001 "
                + "ORDER BY created_at DESC,id DESC LIMIT 1) latest)", USER_ID);

        Long runId = reconciliationService.run();

        assertThat(runId).isNotNull();
        assertThat(reconciliationService.countOpenDifferences()).isPositive();
        assertThat(count("SELECT paused FROM platform_operation_switch WHERE operation_type='WITHDRAW'"))
                .isEqualTo(1);
        assertThat(count("SELECT withdraw_frozen FROM user_risk_control WHERE user_id=?", USER_ID))
                .isEqualTo(1);
    }

    @Test
    @Order(7)
    void shouldFreezeConfirmedDepositRemovedByChainReorganization() throws Exception {
        String snapshot = rpc("evm_snapshot", List.of()).asText();
        String txHash = sendEth(REORG_ADDRESS, new BigDecimal("0.2"));
        awaitReceipt(txHash);
        depositBlockScanner.scanOnce();
        assertThat(count("SELECT status FROM deposit_order WHERE tx_hash=?", txHash)).isEqualTo(1);
        long scannedHeight = count("SELECT last_scanned_block FROM chain_block_scan_record WHERE chain=?", CHAIN);

        assertThat(rpc("evm_revert", List.of(snapshot)).asBoolean()).isTrue();
        BigInteger current = web3j.ethBlockNumber().send().getBlockNumber();
        long replacementBlocks = Math.max(1L, scannedHeight - current.longValueExact() + 1L);
        rpc("anvil_mine", List.of(Numeric.toHexStringWithPrefix(BigInteger.valueOf(replacementBlocks))));
        depositBlockScanner.scanOnce();

        assertThat(count("SELECT status FROM deposit_order WHERE tx_hash=?", txHash)).isEqualTo(2);
        assertThat(count("SELECT risk_status FROM deposit_order WHERE tx_hash=?", txHash)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM asset_risk_freeze_detail risk "
                + "JOIN deposit_order orders ON orders.id=risk.deposit_order_id WHERE orders.tx_hash=?", txHash))
                .isEqualTo(1);
        assertBalanceInvariant(REORG_USER_ID, 7001L);
    }

    private Long prepareWithdrawal(String assetCode, BigDecimal amount, String requestId) {
        Long orderId = withdrawService.apply(USER_ID, request(assetCode, amount, requestId));
        authenticate(REVIEWER_ID, "e2e-reviewer", "REVIEWER");
        assertThat(withdrawService.approveWithdraw(orderId, "E2E approved"))
                .isEqualTo(WithdrawStatus.APPROVED.getCode());
        authenticate(OPERATOR_ID, "e2e-operator", "OPERATOR");
        return orderId;
    }

    private WithdrawApplyRequest request(String assetCode, BigDecimal amount, String requestId) {
        WithdrawApplyRequest request = new WithdrawApplyRequest();
        request.setRequestId(requestId);
        request.setAssetCode(assetCode);
        request.setToAddress(WITHDRAW_ADDRESS);
        request.setAmount(amount);
        return request;
    }

    private void authenticate(long userId, String username, String role) {
        LoginUser principal = new LoginUser(userId, username, role);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, "N/A", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void insertUser(long id, String username, String role) {
        jdbcTemplate.update("INSERT INTO sys_user (id,username,password,role,status) VALUES (?,?,?,?,1)",
                id, username, "not-used", role);
    }

    private void insertDepositAddress(long id, long userId, String address, long derivationIndex) {
        jdbcTemplate.update("INSERT INTO custody_deposit_address "
                        + "(id,user_id,chain,address,custody_type,address_type,key_version,derivation_index,"
                        + "derivation_path,status,assigned_at) VALUES (?,?,?,?,'PLATFORM_CUSTODY','DEPOSIT',"
                        + "'e2e',?,?,1,NOW())",
                id, userId, CHAIN, address, derivationIndex, "m/44'/60'/0'/0/" + derivationIndex);
    }

    private String deployToken() throws Exception {
        String artifactPath = System.getenv("EVM_ERC20_ARTIFACT");
        assertThat(artifactPath).as("EVM_ERC20_ARTIFACT must point to the Forge artifact").isNotBlank();
        JsonNode artifact = objectMapper.readTree(Files.readString(Path.of(artifactPath)));
        String bytecode = artifact.path("bytecode").path("object").asText();
        BigInteger nonce = pendingNonce();
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        RawTransaction transaction = RawTransaction.createContractTransaction(
                nonce, gasPrice, BigInteger.valueOf(2_000_000L), BigInteger.ZERO, bytecode);
        TransactionReceipt receipt = awaitReceipt(broadcast(transaction));
        assertThat(receipt.isStatusOK()).isTrue();
        return receipt.getContractAddress().toLowerCase();
    }

    private String sendEth(String to, BigDecimal amount) throws Exception {
        BigInteger wei = amount.movePointRight(18).toBigIntegerExact();
        RawTransaction transaction = RawTransaction.createEtherTransaction(
                pendingNonce(), web3j.ethGasPrice().send().getGasPrice(),
                BigInteger.valueOf(21_000L), to, wei);
        return broadcast(transaction);
    }

    private String transferToken(String to, BigInteger amount) throws Exception {
        Function transfer = new Function("transfer", List.of(new Address(to), new Uint256(amount)), List.of());
        String data = FunctionEncoder.encode(transfer);
        RawTransaction transaction = RawTransaction.createTransaction(
                pendingNonce(), web3j.ethGasPrice().send().getGasPrice(),
                BigInteger.valueOf(150_000L), tokenAddress, BigInteger.ZERO, data);
        return broadcast(transaction);
    }

    private BigInteger pendingNonce() throws Exception {
        return web3j.ethGetTransactionCount(HOT_WALLET, DefaultBlockParameterName.PENDING)
                .send().getTransactionCount();
    }

    private String broadcast(RawTransaction transaction) throws Exception {
        String raw = Numeric.toHexString(TransactionEncoder.signMessage(transaction, CHAIN_ID, hotWallet));
        var response = web3j.ethSendRawTransaction(raw).send();
        assertThat(response.hasError())
                .as(response.hasError() ? response.getError().getMessage() : "broadcast accepted")
                .isFalse();
        return response.getTransactionHash().toLowerCase();
    }

    private TransactionReceipt awaitReceipt(String txHash) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            var receipt = web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
            if (receipt.isPresent()) {
                return receipt.orElseThrow();
            }
            Thread.sleep(200L);
        }
        throw new AssertionError("transaction receipt was not available: " + txHash);
    }

    private JsonNode rpc(String method, List<?> params) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "jsonrpc", "2.0", "id", 1, "method", method, "params", params));
        HttpRequest request = HttpRequest.newBuilder(URI.create(rpcUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode response = objectMapper.readTree(
                httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
        assertThat(response.path("error").isMissingNode())
                .as("Anvil RPC error: " + response.path("error"))
                .isTrue();
        return response.path("result");
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private BigDecimal amount(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
    }

    private void assertBalanceInvariant(long userId, long assetId) {
        Map<String, Object> balances = jdbcTemplate.queryForMap(
                "SELECT available_balance,frozen_balance,total_balance FROM asset_account "
                        + "WHERE user_id=? AND asset_id=?", userId, assetId);
        BigDecimal available = (BigDecimal) balances.get("available_balance");
        BigDecimal frozen = (BigDecimal) balances.get("frozen_balance");
        BigDecimal total = (BigDecimal) balances.get("total_balance");
        assertThat(total).isEqualByComparingTo(available.add(frozen));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent E2E test interrupted", ex);
        }
    }
}
