package com.example.wallet.infrastructure.web3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RpcRateLimitInterceptorTest {

    @Mock
    private Interceptor.Chain chain;

    @Test
    void shouldSpaceRequestsAccordingToConfiguredRate() throws Exception {
        AtomicLong nanoTime = new AtomicLong();
        List<Long> waits = new ArrayList<>();
        RpcRateLimitInterceptor interceptor = new RpcRateLimitInterceptor(
                2,
                nanoTime::get,
                nanos -> {
                    waits.add(nanos);
                    nanoTime.addAndGet(nanos);
                });
        Request request = request();
        when(chain.request()).thenReturn(request);
        when(chain.proceed(request)).thenReturn(response(request, 200), response(request, 200));

        interceptor.intercept(chain).close();
        interceptor.intercept(chain).close();

        assertThat(waits).containsExactly(TimeUnit.MILLISECONDS.toNanos(500));
    }

    private Request request() {
        return new Request.Builder().url("http://localhost").build();
    }

    private Response response(Request request, int code) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(ResponseBody.create("", MediaType.get("application/json")))
                .build();
    }
}
