package com.example.wallet.infrastructure.signer;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

final class SignerMtls {
    private SignerMtls() {}

    static RestClient.Builder configure(RestClient.Builder builder, SignerProperties properties) {
        if (!StringUtils.hasText(properties.getClientKeyStore())
                && !StringUtils.hasText(properties.getTrustStore())) return builder;
        try {
            require(properties.getClientKeyStore(), "signer client key store");
            require(properties.getClientKeyStorePassword(), "signer client key store password");
            require(properties.getTrustStore(), "signer trust store");
            require(properties.getTrustStorePassword(), "signer trust store password");
            KeyStore keys = load(properties.getClientKeyStore(), properties.getClientKeyStorePassword());
            KeyStore trust = load(properties.getTrustStore(), properties.getTrustStorePassword());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keys, properties.getClientKeyStorePassword().toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trust);
            SSLContext context = SSLContext.getInstance("TLSv1.3");
            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            HttpClient client = HttpClient.newBuilder().sslContext(context)
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            return builder.requestFactory(new JdkClientHttpRequestFactory(client));
        } catch (Exception ex) {
            throw new IllegalStateException("cannot configure signer mTLS", ex);
        }
    }

    private static KeyStore load(String path, String password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(Path.of(path))) {
            store.load(input, password.toCharArray());
        }
        return store;
    }
    private static void require(String value, String name) {
        if (!StringUtils.hasText(value)) throw new IllegalStateException(name + " is required");
    }
}
