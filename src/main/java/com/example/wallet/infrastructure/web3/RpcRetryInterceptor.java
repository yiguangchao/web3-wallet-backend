package com.example.wallet.infrastructure.web3;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import okhttp3.Response;

public class RpcRetryInterceptor implements Interceptor {

    private static final Set<Integer> RETRYABLE_STATUS_CODES =
            Set.of(408, 429, 500, 502, 503, 504);

    private final int maxRetries;
    private final long initialBackoffMillis;
    private final long maxBackoffMillis;
    private final NanoSleeper sleeper;

    public RpcRetryInterceptor(int maxRetries, long initialBackoffMillis, long maxBackoffMillis) {
        this(maxRetries, initialBackoffMillis, maxBackoffMillis, RpcRetryInterceptor::sleep);
    }

    RpcRetryInterceptor(int maxRetries,
                        long initialBackoffMillis,
                        long maxBackoffMillis,
                        NanoSleeper sleeper) {
        this.maxRetries = Math.max(0, maxRetries);
        this.initialBackoffMillis = Math.max(0, initialBackoffMillis);
        this.maxBackoffMillis = Math.max(this.initialBackoffMillis, maxBackoffMillis);
        this.sleeper = sleeper;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        IOException lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            Response response;
            try {
                response = chain.proceed(chain.request());
            } catch (IOException ex) {
                lastException = ex;
                if (attempt == maxRetries) {
                    throw ex;
                }
                sleepBeforeRetry(backoffMillis(attempt));
                continue;
            }
            if (!isRetryable(response.code()) || attempt == maxRetries) {
                return response;
            }
            long delayMillis = retryDelayMillis(response, attempt);
            response.close();
            sleepBeforeRetry(delayMillis);
        }
        throw lastException != null ? lastException : new IOException("RPC request failed after retries");
    }

    private boolean isRetryable(int statusCode) {
        return RETRYABLE_STATUS_CODES.contains(statusCode);
    }

    private long retryDelayMillis(Response response, int attempt) {
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null) {
            try {
                long requestedDelay = TimeUnit.SECONDS.toMillis(Long.parseLong(retryAfter));
                return Math.min(maxBackoffMillis, Math.max(backoffMillis(attempt), requestedDelay));
            } catch (NumberFormatException ignored) {
                // Retry-After may be an HTTP date; use exponential backoff when it is not a number.
            }
        }
        return backoffMillis(attempt);
    }

    private long backoffMillis(int attempt) {
        long multiplier = 1L << Math.min(attempt, 30);
        long delay;
        try {
            delay = Math.multiplyExact(initialBackoffMillis, multiplier);
        } catch (ArithmeticException ex) {
            delay = Long.MAX_VALUE;
        }
        return Math.min(delay, maxBackoffMillis);
    }

    private void sleepBeforeRetry(long delayMillis) throws IOException {
        if (delayMillis > 0) {
            sleeper.sleep(TimeUnit.MILLISECONDS.toNanos(delayMillis));
        }
    }

    private static void sleep(long nanos) throws IOException {
        try {
            TimeUnit.NANOSECONDS.sleep(nanos);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during RPC retry backoff", ex);
        }
    }

    @FunctionalInterface
    interface NanoSleeper {
        void sleep(long nanos) throws IOException;
    }
}