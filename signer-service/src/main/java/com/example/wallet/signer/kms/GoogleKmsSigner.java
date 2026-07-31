package com.example.wallet.signer.kms;

import com.google.cloud.kms.v1.AsymmetricSignResponse;
import com.google.cloud.kms.v1.CryptoKeyVersionName;
import com.google.cloud.kms.v1.Digest;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.kms.v1.PublicKey;
import com.google.protobuf.ByteString;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Locale;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

@Component
public class GoogleKmsSigner {
    private final KeyManagementServiceClient client;
    public GoogleKmsSigner(KeyManagementServiceClient client) { this.client = client; }

    public KmsSignature sign(String keyVersionName, byte[] transactionHash) {
        CryptoKeyVersionName.parse(keyVersionName);
        PublicKey publicKey = client.getPublicKey(keyVersionName);
        if (!publicKey.getAlgorithm().name().equals("EC_SIGN_SECP256K1_SHA256")) {
            throw new IllegalStateException("KMS key algorithm must be EC_SIGN_SECP256K1_SHA256");
        }
        BigInteger publicPoint = parsePublicKey(publicKey.getPem());
        AsymmetricSignResponse response = client.asymmetricSign(keyVersionName,
                Digest.newBuilder().setSha256(ByteString.copyFrom(transactionHash)).build());
        BigInteger[] signature = parseDer(response.getSignature().toByteArray());
        return new KmsSignature(signature[0], signature[1], publicPoint,
                "0x" + Keys.getAddress(publicPoint).toLowerCase(Locale.ROOT));
    }

    public String publicAddress(String keyVersionName) {
        CryptoKeyVersionName.parse(keyVersionName);
        PublicKey publicKey = client.getPublicKey(keyVersionName);
        if (!publicKey.getAlgorithm().name().equals("EC_SIGN_SECP256K1_SHA256"))
            throw new IllegalStateException("KMS key algorithm must be EC_SIGN_SECP256K1_SHA256");
        return "0x" + Keys.getAddress(parsePublicKey(publicKey.getPem())).toLowerCase(Locale.ROOT);
    }

    private BigInteger parsePublicKey(String pem) {
        try {
            String base64 = pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
            byte[] encoded = java.util.Base64.getDecoder().decode(base64);
            byte[] point = SubjectPublicKeyInfo.getInstance(encoded).getPublicKeyData().getBytes();
            if (point.length != 65 || point[0] != 4) {
                throw new IllegalStateException("KMS public key is not uncompressed secp256k1");
            }
            return Numeric.toBigInt(Arrays.copyOfRange(point, 1, point.length));
        } catch (Exception ex) { throw new IllegalStateException("cannot parse KMS public key", ex); }
    }

    private BigInteger[] parseDer(byte[] der) {
        try {
            ASN1Sequence sequence = ASN1Sequence.getInstance(der);
            return new BigInteger[]{
                    ((ASN1Integer) sequence.getObjectAt(0)).getPositiveValue(),
                    ((ASN1Integer) sequence.getObjectAt(1)).getPositiveValue()};
        } catch (Exception ex) { throw new IllegalStateException("invalid KMS ECDSA signature", ex); }
    }
}
