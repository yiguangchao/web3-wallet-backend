package com.example.wallet.infrastructure.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RpcRetryInterceptorTest {

    @Mock
    private Interceptor.Chain chain;

    private Request request;

    @BeforeEach
    void setUp() {
        request = new Request.Builder().url("http://localhost").build();
        when(chain.request()).thenReturn(request);
    }

    @Test
    void shouldRetryRetryableHttpStatusWithExponentialBackoff() throws Exception {
        List<Long> waits = new ArrayList<>();
        RpcRetryInterceptor interceptor = new RpcRetryInterceptor(2, 100, 1_000, waits::add);
        when(chain.proceed(request)).thenReturn(response(503), response(200));

        Response result = interceptor.intercept(chain);

        assertThat(result.code()).isEqualTo(200);
        assertThat(waits).containsExactly(TimeUnit.MILLISECONDS.toNanos(100));
        verify(chain, times(2)).proceed(request);
    }

    @Test
    void shouldHonorNumericRetryAfterHeader() throws Exception {
        List<Long> waits = new ArrayList<>();
        RpcRetryInterceptor interceptor = new RpcRetryInterceptor(1, 100, 5_000, waits::add);
        when(chain.proceed(request)).thenReturn(
                response(429).newBuilder().header("Retry-After", "2").build(),
                response(200));

        interceptor.intercept(chain).close();

        assertThat(waits).containsExactly(TimeUnit.SECONDS.toNanos(2));
    }

    @Test
    void shouldRetryIoException() throws Exception {
        List<Long> waits = new ArrayList<>();
        RpcRetryInterceptor interceptor = new RpcRetryInterceptor(1, 50, 500, waits::add);
        when(chain.proceed(request)).thenThrow(new IOException("connection reset")).thenReturn(response(200));

        Response result = interceptor.intercept(chain);

        assertThat(result.code()).isEqualTo(200);
        assertThat(waits).containsExactly(TimeUnit.MILLISECONDS.toNanos(50));
    }

    @Test
    void shouldNotRetryNonRetryableStatus() throws Exception {
        RpcRetryInterceptor interceptor = new RpcRetryInterceptor(2, 100, 1_000, nanos -> {
        });
        when(chain.proceed(request)).thenReturn(response(400));

        Response result = interceptor.intercept(chain);

        assertThat(result.code()).isEqualTo(400);
        verify(chain).proceed(request);
    }

    private Response response(int code) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(ResponseBody.create("", MediaType.get("application/json")))
                .build();
    }
}
