package com.example.wallet.infrastructure.redis;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisDistributedLock {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<LockHandle> tryLock(String key, Duration leaseTime) {
        String ownerId = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, ownerId, leaseTime);
        return Boolean.TRUE.equals(acquired)
                ? Optional.of(new LockHandle(key, ownerId))
                : Optional.empty();
    }

    public boolean renew(LockHandle handle, Duration leaseTime) {
        Long result = redisTemplate.execute(
                RENEW_SCRIPT,
                Collections.singletonList(handle.key()),
                handle.ownerId(),
                String.valueOf(leaseTime.toMillis()));
        return Long.valueOf(1L).equals(result);
    }

    public void unlock(LockHandle handle) {
        redisTemplate.execute(
                RELEASE_SCRIPT,
                Collections.singletonList(handle.key()),
                handle.ownerId());
    }

    public record LockHandle(String key, String ownerId) {
    }
}
