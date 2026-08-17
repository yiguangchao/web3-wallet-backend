package com.example.wallet.signer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class SignerFlywayMySqlMigrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "SIGNER_TEST_MYSQL_URL", matches = ".+")
    void migratesEmptySignerDatabaseToLatestVersion() {
        Flyway flyway = Flyway.configure()
                .dataSource(System.getenv("SIGNER_TEST_MYSQL_URL"),
                        System.getenv("SIGNER_TEST_MYSQL_USERNAME"),
                        System.getenv("SIGNER_TEST_MYSQL_PASSWORD"))
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.clean();
        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(6);
        assertThat(flyway.info().pending()).isEmpty();
    }
}
