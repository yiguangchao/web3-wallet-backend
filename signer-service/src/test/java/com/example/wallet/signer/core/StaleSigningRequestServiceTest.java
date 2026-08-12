package com.example.wallet.signer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.wallet.signer.config.SignerProperties;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class StaleSigningRequestServiceTest {
    private JdbcTemplate jdbc;
    private StaleSigningRequestService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        SignerProperties properties = new SignerProperties();
        properties.setProcessingAlertSeconds(300);
        service = new StaleSigningRequestService(jdbc, properties);
    }

    @Test
    void listsStaleProcessingRequestsWithBoundedResultSize() {
        StaleSigningRequest request = new StaleSigningRequest("withdraw-v1:1:000000",
                LocalDateTime.now().minusMinutes(10), LocalDateTime.now().minusMinutes(6));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers
                .<RowMapper<StaleSigningRequest>>any(), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(request));

        assertThat(service.list()).containsExactly(request);

        verify(jdbc).query(anyString(), org.mockito.ArgumentMatchers
                .<RowMapper<StaleSigningRequest>>any(), any(LocalDateTime.class), eq(100));
    }

    @Test
    void treatsNullStaleCountAsZero() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(LocalDateTime.class))).thenReturn(null);

        assertThat(service.count()).isZero();
    }
}
