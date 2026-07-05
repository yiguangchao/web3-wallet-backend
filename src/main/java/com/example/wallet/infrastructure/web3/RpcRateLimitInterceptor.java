package com.example.wallet.infrastructure.web3;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import okhttp3.Interceptor;
import okhttp3.Response;

public class RpcRateLimitInterceptor implements Interceptor {

    private final long intervalNanos;
    private final LongSupplier nanoTime;
    private final NanoSleeper sleeper;
    private long nextAllowedNanos;

    public RpcRateLimitInterceptor(int maxRequestsPerSecond) {
        this(maxRequestsPerSecond, System::nanoTime, RpcRateLimitInterceptor::sleep);
    }

    RpcRateLimitInterceptor(int maxRequestsPerSecond, LongSupplier nanoTime, NanoSleeper sleeper) {
        this.intervalNanos = maxRequestsPerSecond > 0
                ? TimeUnit.SECONDS.toNanos(1) / maxRequestsPerSecond
                : 0;
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        long waitNanos = reserveWaitTime();
        if (waitNanos > 0) {
            sleeper.sleep(waitNanos);
        }
        return chain.proceed(chain.request());
    }

    private synchronized long reserveWaitTime() {
        if (intervalNanos == 0) {
            return 0;
        }
        long now = nanoTime.getAsLong();
        long scheduled = Math.max(now, nextAllowedNanos);
        nextAllowedNanos = scheduled + intervalNanos;
        return scheduled - now;
    }

    private static void sleep(long nanos) throws IOException {
        try {
            TimeUnit.NANOSECONDS.sleep(nanos);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for RPC rate limit", ex);
        }
    }

    @FunctionalInterface
    interface NanoSleeper {
        void sleep(long nanos) throws IOException;
    }
}
