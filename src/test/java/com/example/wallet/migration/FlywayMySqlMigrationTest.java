package com.example.wallet.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMySqlMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
            .withDatabaseName("wallet_migration_test")
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void cleanDatabase() {
        flyway(null).clean();
    }

    @Test
    void shouldMigrateEmptyDatabaseFromV1ToLatest() throws Exception {
        Flyway flyway = flyway(null);

        flyway.migrate();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("12");
        assertThat(count("SELECT COUNT(*) FROM supported_asset")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = 'asset_freeze_detail'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND column_name = 'asset_id' "
                + "AND table_name IN ('asset_account','asset_flow','deposit_order',"
                + "'withdraw_order','custody_sweep_order')")).isEqualTo(5);
    }

    @Test
    void shouldUpgradeKnownV6DataToV7AndV8() throws Exception {
        flyway("6").migrate();
        execute("INSERT INTO wallet_address "
                + "(id,user_id,chain,address,address_type,status) VALUES "
                + "(1,10,'eth_sepolia','0x1111111111111111111111111111111111111111','EXTERNAL',1)");
        execute("INSERT INTO asset_account "
                + "(id,user_id,chain,token_symbol,token_address) VALUES "
                + "(1,10,'ETH_SEPOLIA','ETH',NULL)");

        Flyway latest = flyway(null);
        latest.migrate();

        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("12");
        assertThat(count("SELECT COUNT(*) FROM asset_account WHERE asset_id = 7001")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM wallet_address WHERE verified_at IS NULL")).isEqualTo(1);
    }

    @Test
    void shouldFailUpgradeWhenExternalAddressBelongsToMultipleUsers() throws Exception {
        flyway("6").migrate();
        execute("INSERT INTO wallet_address "
                + "(id,user_id,chain,address,address_type,status) VALUES "
                + "(1,10,'ETH_SEPOLIA','0x1111111111111111111111111111111111111111','EXTERNAL',1),"
                + "(2,20,'ETH_SEPOLIA','0x1111111111111111111111111111111111111111','EXTERNAL',1)");

        assertThatThrownBy(() -> flyway(null).migrate())
                .hasMessageContaining("V7__external_wallet_signature_verification.sql");
    }

    @Test
    void shouldFailUpgradeForInvalidEvmAddress() throws Exception {
        flyway("6").migrate();
        execute("INSERT INTO wallet_address "
                + "(id,user_id,chain,address,address_type,status) VALUES "
                + "(1,10,'ETH_SEPOLIA','0xzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz','EXTERNAL',1)");

        assertThatThrownBy(() -> flyway(null).migrate())
                .hasMessageContaining("V8__supported_asset_and_custody_hardening.sql");
    }

    @Test
    void shouldFailUpgradeInsteadOfMergingDuplicateNativeAccounts() throws Exception {
        flyway("7").migrate();
        execute("INSERT INTO asset_account "
                + "(id,user_id,chain,token_symbol,token_address,available_balance,total_balance) VALUES "
                + "(1,10,'ETH_SEPOLIA','ETH',NULL,1,1),"
                + "(2,10,'ETH_SEPOLIA','ETH',NULL,2,2)");

        assertThatThrownBy(() -> flyway(null).migrate())
                .hasMessageContaining("V8__supported_asset_and_custody_hardening.sql");
    }

    @Test
    void shouldBackfillWithdrawalFreezeDetailsWhenUpgradingToV9() throws Exception {
        flyway("8").migrate();
        execute("INSERT INTO withdraw_order "
                + "(id,user_id,asset_id,chain,token_symbol,to_address,amount,fee,status,tx_hash) VALUES "
                + "(1,10,7001,'ETH_SEPOLIA','ETH','0x1111111111111111111111111111111111111111',1,0.1,0,NULL),"
                + "(2,10,7001,'ETH_SEPOLIA','ETH','0x2222222222222222222222222222222222222222',2,0.1,3,'0xconfirmed'),"
                + "(3,10,7001,'ETH_SEPOLIA','ETH','0x3333333333333333333333333333333333333333',3,0.1,4,'0xfailed'),"
                + "(4,10,7001,'ETH_SEPOLIA','ETH','0x4444444444444444444444444444444444444444',4,0.1,1,NULL)");

        flyway(null).migrate();

        assertThat(count("SELECT COUNT(*) FROM asset_freeze_detail WHERE status = 0")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM asset_freeze_detail WHERE status = 1 "
                + "AND settled_at IS NOT NULL")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM asset_freeze_detail WHERE status = 2 "
                + "AND settled_at IS NOT NULL")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM asset_freeze_detail "
                + "WHERE frozen_amount = principal_amount + fee_amount")).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM withdraw_order WHERE id = 3 AND status = 5 "
                + "AND status_changed_at IS NOT NULL")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM withdraw_order WHERE id = 4 AND status = 4 "
                + "AND manual_review_reason IS NOT NULL")).isEqualTo(1);
    }

    @Test
    void shouldEnforceNewUniqueIndexes() throws Exception {
        flyway(null).migrate();
        execute("INSERT INTO asset_account "
                + "(id,user_id,asset_id,chain,token_symbol,token_address) VALUES "
                + "(1,10,7001,'ETH_SEPOLIA','ETH',NULL)");

        assertThatThrownBy(() -> execute("INSERT INTO asset_account "
                + "(id,user_id,asset_id,chain,token_symbol,token_address) VALUES "
                + "(2,10,7001,'ETH_SEPOLIA','ETH',NULL)"))
                .isInstanceOf(SQLException.class);
        execute("INSERT INTO wallet_address "
                + "(id,user_id,chain,address,address_type,status) VALUES "
                + "(1,10,'ETH_SEPOLIA','0x1111111111111111111111111111111111111111','EXTERNAL',1)");
        assertThatThrownBy(() -> execute("INSERT INTO wallet_address "
                + "(id,user_id,chain,address,address_type,status) VALUES "
                + "(2,20,'ETH_SEPOLIA','0x1111111111111111111111111111111111111111','EXTERNAL',1)"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void shouldEnforceLedgerInvariantsAndBusinessUniqueness() throws Exception {
        flyway(null).migrate();
        execute("INSERT INTO asset_account "
                + "(id,user_id,asset_id,chain,token_symbol,available_balance,frozen_balance,total_balance) VALUES "
                + "(1,10,7001,'ETH_SEPOLIA','ETH',8,2,10)");

        assertThatThrownBy(() -> execute("INSERT INTO asset_account "
                + "(id,user_id,asset_id,chain,token_symbol,available_balance,frozen_balance,total_balance) VALUES "
                + "(2,20,7001,'ETH_SEPOLIA','ETH',8,2,11)"))
                .isInstanceOf(SQLException.class);

        execute("INSERT INTO asset_flow "
                + "(id,user_id,asset_id,chain,token_symbol,business_type,business_id,amount) VALUES "
                + "(1,10,7001,'ETH_SEPOLIA','ETH','DEPOSIT',100,1)");
        assertThatThrownBy(() -> execute("INSERT INTO asset_flow "
                + "(id,user_id,asset_id,chain,token_symbol,business_type,business_id,amount) VALUES "
                + "(2,10,7001,'ETH_SEPOLIA','ETH','DEPOSIT',100,1)"))
                .isInstanceOf(SQLException.class);

        execute("INSERT INTO asset_freeze_detail "
                + "(id,user_id,asset_id,business_type,business_id,principal_amount,fee_amount,"
                + "frozen_amount,status,frozen_at) VALUES "
                + "(1,10,7001,'WITHDRAW',200,1,0.1,1.1,0,NOW())");
        assertThatThrownBy(() -> execute("INSERT INTO asset_freeze_detail "
                + "(id,user_id,asset_id,business_type,business_id,principal_amount,fee_amount,"
                + "frozen_amount,status,frozen_at) VALUES "
                + "(2,10,7001,'WITHDRAW',200,1,0.1,1.1,0,NOW())"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("INSERT INTO asset_freeze_detail "
                + "(id,user_id,asset_id,business_type,business_id,principal_amount,fee_amount,"
                + "frozen_amount,status,frozen_at) VALUES "
                + "(3,10,7001,'WITHDRAW',201,1,0.1,1.2,0,NOW())"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void shouldEnforceWithdrawStateAndManualReviewReason() throws Exception {
        flyway(null).migrate();

        assertThatThrownBy(() -> execute("INSERT INTO withdraw_order "
                + "(id,user_id,asset_id,chain_id,chain,token_symbol,to_address,amount,fee,status) VALUES "
                + "(1,10,7001,11155111,'ETH_SEPOLIA','ETH','0x1111111111111111111111111111111111111111',1,0.1,4)"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("INSERT INTO withdraw_order "
                + "(id,user_id,asset_id,chain_id,chain,token_symbol,to_address,amount,fee,status) VALUES "
                + "(2,10,7001,11155111,'ETH_SEPOLIA','ETH','0x2222222222222222222222222222222222222222',1,0.1,10)"))
                .isInstanceOf(SQLException.class);
        execute("INSERT INTO withdraw_order "
                + "(id,user_id,asset_id,chain_id,chain,token_symbol,to_address,amount,fee,status,manual_review_reason) VALUES "
                + "(3,10,7001,11155111,'ETH_SEPOLIA','ETH','0x3333333333333333333333333333333333333333',1,0.1,4,'rpc uncertain')");
        assertThat(count("SELECT COUNT(*) FROM withdraw_order WHERE id = 3 AND status = 4"))
                .isEqualTo(1);
    }

    @Test
    void shouldEnforceWalletNonceAndWithdrawalNonceUniqueness() throws Exception {
        flyway(null).migrate();
        String wallet = "0x1111111111111111111111111111111111111111";
        execute("INSERT INTO wallet_nonce (id,chain_id,hot_wallet_address,next_nonce) VALUES "
                + "(1,11155111,'" + wallet + "',10)");
        assertThatThrownBy(() -> execute(
                "INSERT INTO wallet_nonce (id,chain_id,hot_wallet_address,next_nonce) VALUES "
                        + "(2,11155111,'" + wallet + "',11)"))
                .isInstanceOf(SQLException.class);

        execute("INSERT INTO withdraw_order "
                + "(id,user_id,asset_id,chain_id,chain,token_symbol,to_address,amount,fee,status,"
                + "hot_wallet_address,nonce,signer_key_id) VALUES "
                + "(10,10,7001,11155111,'ETH_SEPOLIA','ETH','0x2222222222222222222222222222222222222222',"
                + "1,0.1,7,'" + wallet + "',10,'withdraw-v1')");
        assertThatThrownBy(() -> execute("INSERT INTO withdraw_order "
                + "(id,user_id,asset_id,chain_id,chain,token_symbol,to_address,amount,fee,status,"
                + "hot_wallet_address,nonce,signer_key_id) VALUES "
                + "(11,20,7001,11155111,'ETH_SEPOLIA','ETH','0x3333333333333333333333333333333333333333',"
                + "1,0.1,7,'" + wallet + "',10,'withdraw-v1')"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void shouldEnforceOneChainTransactionAndOutboxPerWithdrawal() throws Exception {
        flyway(null).migrate();
        String wallet = "0x1111111111111111111111111111111111111111";
        String recipient = "0x2222222222222222222222222222222222222222";
        String hash = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        execute("INSERT INTO withdraw_order "
                + "(id,user_id,asset_id,chain_id,chain,token_symbol,to_address,amount,fee,status,"
                + "hot_wallet_address,nonce,signer_key_id) VALUES "
                + "(10,10,7001,11155111,'ETH_SEPOLIA','ETH','" + recipient + "',"
                + "1,0.1,1,'" + wallet + "',10,'withdraw-v1')");
        execute("INSERT INTO withdraw_chain_transaction "
                + "(id,withdraw_order_id,chain_id,hot_wallet_address,nonce,signer_key_id,"
                + "transaction_type,to_address,value_wei,transaction_data,gas_price,gas_limit,"
                + "raw_transaction,tx_hash,status) VALUES "
                + "(20,10,11155111,'" + wallet + "',10,'withdraw-v1','NATIVE','" + recipient + "',"
                + "1,'0x',1,21000,'0xraw','" + hash + "',0)");
        execute("INSERT INTO transaction_outbox "
                + "(id,aggregate_type,aggregate_id,chain_transaction_id,status,attempt_count,next_retry_at) "
                + "VALUES (30,'WITHDRAWAL',10,20,0,0,NOW())");

        assertThatThrownBy(() -> execute("INSERT INTO transaction_outbox "
                + "(id,aggregate_type,aggregate_id,chain_transaction_id,status,attempt_count) "
                + "VALUES (31,'WITHDRAWAL',10,20,0,0)"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE transaction_outbox "
                + "SET status=1, locked_by=NULL, locked_at=NULL WHERE id=30"))
                .isInstanceOf(SQLException.class);
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long count(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
