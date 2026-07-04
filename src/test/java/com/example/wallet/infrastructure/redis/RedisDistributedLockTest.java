package com.example.wallet.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.wallet.infrastructure.redis.RedisDistributedLock.LockHandle;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisDistributedLockTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisDistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        distributedLock = new RedisDistributedLock(redisTemplate);
    }

    @Test
    void shouldReturnHandleWhenLockIsAcquired() {
        Duration leaseTime = Duration.ofSeconds(30);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("scan-lock"), anyString(), eq(leaseTime))).thenReturn(true);

        Optional<LockHandle> handle = distributedLock.tryLock("scan-lock", leaseTime);

        assertThat(handle).isPresent();
        assertThat(handle.orElseThrow().key()).isEqualTo("scan-lock");
        assertThat(handle.orElseThrow().ownerId()).isNotBlank();
    }

    @Test
    void shouldReturnEmptyWhenLockIsHeldByAnotherInstance() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("scan-lock"), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(distributedLock.tryLock("scan-lock", Duration.ofSeconds(30))).isEmpty();
    }

    @Test
    void shouldRenewAndReleaseOnlyThroughOwnerCheckingScripts() {
        LockHandle handle = new LockHandle("scan-lock", "instance-a");
        Duration leaseTime = Duration.ofSeconds(30);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(Collections.singletonList("scan-lock")),
                eq("instance-a"),
                eq("30000"))).thenReturn(1L);

        assertThat(distributedLock.renew(handle, leaseTime)).isTrue();
        distributedLock.unlock(handle);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(Collections.singletonList("scan-lock")),
                eq("instance-a"));
    }
}
