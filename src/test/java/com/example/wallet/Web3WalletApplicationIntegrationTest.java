package com.example.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.infrastructure.custody.CustodyKeyService;
import com.example.wallet.infrastructure.custody.DerivedCustodyAddress;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.service.AssetService;
import com.example.wallet.module.asset.service.SupportedAssetService;
import com.example.wallet.module.wallet.dto.AllocateDepositAddressRequest;
import com.example.wallet.module.wallet.dto.DepositAddressResponse;
import com.example.wallet.module.wallet.service.WalletService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class Web3WalletApplicationIntegrationTest {

    private static final String WALLET_ADDRESS = "0x1111111111111111111111111111111111111111";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withCommand("--log-bin-trust-function-creators=1")
            .withDatabaseName("web3_wallet_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WalletService walletService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private SupportedAssetService supportedAssetService;

    @MockBean
    private Web3Service web3Service;

    @MockBean
    private CustodyKeyService custodyKeyService;

    @Test
    void shouldRegisterLoginAndAccessSecuredWalletApis() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "integration_user",
                                  "password": "secret123",
                                  "email": "integration@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "integration_user",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginBody.path("data").path("token").asText();
        assertThat(token).isNotBlank();

        when(custodyKeyService.deriveAddress("v1", 0L)).thenReturn(
                new DerivedCustodyAddress(WALLET_ADDRESS, "v1", 0L, "m/44'/60'/0'/0/0"));
        mockMvc.perform(post("/api/wallet/deposit-address")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chain": "ETH_SEPOLIA"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.address").value(WALLET_ADDRESS));

        mockMvc.perform(get("/api/wallet/deposit-addresses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].address").value(WALLET_ADDRESS));
    }

    @Test
    void shouldReadAndWriteRedis() {
        redisTemplate.opsForValue().set("test:health", "ok", Duration.ofSeconds(30));

        assertThat(redisTemplate.opsForValue().get("test:health")).isEqualTo("ok");
    }

    @Test
    void shouldExposeContainerHealthAndOpenApiMetadata() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.version").value("1.0.0-controlled"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @Test
    void shouldAllocateFiftyUniqueAddressesAndRemainIdempotentForSameUser() {
        when(custodyKeyService.deriveAddress(eq("v1"), anyLong())).thenAnswer(invocation -> {
            long index = invocation.getArgument(1);
            String address = "0x" + String.format("%040x", index + 1);
            return new DerivedCustodyAddress(address, "v1", index, "m/44'/60'/0'/0/" + index);
        });
        AllocateDepositAddressRequest request = new AllocateDepositAddressRequest();
        request.setChain("ETH_SEPOLIA");
        for (long id = 20_000; id < 20_050; id++) {
            jdbcTemplate.update("INSERT INTO sys_user (id,username,password,status) VALUES (?,?,?,1)",
                    id, "concurrent_" + id, "not-used");
        }

        List<DepositAddressResponse> allocated = concurrentAllocations(
                java.util.stream.LongStream.range(20_000, 20_050).boxed().toList(), request);

        assertThat(allocated).extracting(DepositAddressResponse::address).doesNotHaveDuplicates();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT derivation_index) FROM custody_deposit_address "
                        + "WHERE user_id BETWEEN 20000 AND 20049", Long.class)).isEqualTo(50L);

        List<DepositAddressResponse> repeated = concurrentAllocations(
                java.util.Collections.nCopies(20, 20_000L), request);
        assertThat(repeated).extracting(DepositAddressResponse::address)
                .containsOnly(repeated.get(0).address());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM custody_deposit_address WHERE user_id = 20000 AND status = 1",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void shouldCreateOnlyOneNativeAssetAccountUnderConcurrentCredits() {
        long userId = 99_001L;
        SupportedAsset asset = supportedAssetService.getRequiredByAssetCode("ETH");
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (long businessId = 80_000; businessId < 80_020; businessId++) {
                long id = businessId;
                futures.add(CompletableFuture.runAsync(() -> {
                    await(start);
                    assetService.creditDeposit(userId, asset, BigDecimal.ONE, id, "0x" + id);
                }, executor));
            }
            start.countDown();
            futures.forEach(CompletableFuture::join);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM asset_account WHERE user_id = ? AND asset_id = ?",
                Long.class, userId, asset.getId())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT available_balance FROM asset_account WHERE user_id = ? AND asset_id = ?",
                BigDecimal.class, userId, asset.getId())).isEqualByComparingTo("20");
    }

    @Test
    void shouldAllowOnlyConfirmOrReleaseForTheSameWithdrawal() {
        long userId = 99_002L;
        long businessId = 88_001L;
        SupportedAsset asset = supportedAssetService.getRequiredByAssetCode("ETH");
        jdbcTemplate.update("DELETE FROM asset_flow WHERE business_id = ?", businessId);
        jdbcTemplate.update("DELETE FROM asset_freeze_detail WHERE business_id = ?", businessId);
        jdbcTemplate.update("DELETE FROM asset_account WHERE user_id = ? AND asset_id = ?",
                userId, asset.getId());
        jdbcTemplate.update("""
                INSERT INTO asset_account (
                    id,user_id,asset_id,chain,token_symbol,token_address,
                    available_balance,frozen_balance,total_balance
                ) VALUES (?,?,?,?,?,?,100,0,100)
                """, 91_001L, userId, asset.getId(), asset.getChain(), asset.getSymbol(), null);
        assetService.freezeWithdrawal(userId, asset, new BigDecimal("10"), businessId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            CompletableFuture<Boolean> confirm = CompletableFuture.supplyAsync(() -> {
                await(start);
                try {
                    assetService.confirmWithdrawal(userId, asset, businessId, "0xconfirm");
                    return true;
                } catch (RuntimeException ex) {
                    return false;
                }
            }, executor);
            CompletableFuture<Boolean> release = CompletableFuture.supplyAsync(() -> {
                await(start);
                try {
                    assetService.releaseWithdrawal(userId, asset, businessId, "0xrelease");
                    return true;
                } catch (RuntimeException ex) {
                    return false;
                }
            }, executor);
            start.countDown();
            assertThat(List.of(confirm.join(), release.join()).stream().filter(Boolean::booleanValue).count())
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        var balances = jdbcTemplate.queryForMap(
                "SELECT available_balance,frozen_balance,total_balance FROM asset_account "
                        + "WHERE user_id = ? AND asset_id = ?", userId, asset.getId());
        BigDecimal available = (BigDecimal) balances.get("available_balance");
        BigDecimal frozen = (BigDecimal) balances.get("frozen_balance");
        BigDecimal total = (BigDecimal) balances.get("total_balance");
        assertThat(frozen).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(total).isEqualByComparingTo(available.add(frozen));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM asset_flow WHERE business_id = ? "
                        + "AND business_type IN ('WITHDRAW_CONFIRM','WITHDRAW_RELEASE')",
                Long.class, businessId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM asset_freeze_detail WHERE business_id = ?",
                Integer.class, businessId)).isIn(1, 2);
    }

    private List<DepositAddressResponse> concurrentAllocations(
            List<Long> userIds, AllocateDepositAddressRequest request) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(50, userIds.size()));
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<CompletableFuture<DepositAddressResponse>> futures = userIds.stream()
                    .map(userId -> CompletableFuture.supplyAsync(() -> {
                        await(start);
                        return walletService.allocateDepositAddress(userId, request);
                    }, executor))
                    .toList();
            start.countDown();
            return futures.stream().map(CompletableFuture::join).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrent test interrupted", ex);
        }
    }
}
