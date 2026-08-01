package com.example.wallet.signer.kms;

import com.google.cloud.kms.v1.AsymmetricSignRequest;
import com.google.cloud.kms.v1.AsymmetricSignResponse;
import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm;
import com.google.cloud.kms.v1.CryptoKeyVersionName;
import com.google.cloud.kms.v1.Digest;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.kms.v1.PublicKey;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int64Value;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.CRC32C;
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
        if (transactionHash == null || transactionHash.length != 32) {
            throw new IllegalArgumentException("transaction hash must be 32 bytes");
        }
        PublicKey publicKey = verifiedPublicKey(keyVersionName);
        BigInteger publicPoint = parsePublicKey(publicKey.getPem());

        ByteString digestBytes = ByteString.copyFrom(transactionHash);
        AsymmetricSignRequest request = AsymmetricSignRequest.newBuilder()
                .setName(keyVersionName)
                .setDigest(Digest.newBuilder().setSha256(digestBytes).build())
                .setDigestCrc32C(Int64Value.of(crc32c(transactionHash)))
                .build();
        AsymmetricSignResponse response = client.asymmetricSign(request);
        byte[] signatureBytes = verifiedSignature(keyVersionName, response);
        BigInteger[] signature = parseDer(signatureBytes);
        return new KmsSignature(signature[0], signature[1], publicPoint,
                "0x" + Keys.getAddress(publicPoint).toLowerCase(Locale.ROOT));
    }

    public String publicAddress(String keyVersionName) {
        CryptoKeyVersionName.parse(keyVersionName);
        PublicKey publicKey = verifiedPublicKey(keyVersionName);
        return "0x" + Keys.getAddress(parsePublicKey(publicKey.getPem())).toLowerCase(Locale.ROOT);
    }

    private PublicKey verifiedPublicKey(String keyVersionName) {
        PublicKey publicKey = client.getPublicKey(keyVersionName);
        if (!keyVersionName.equals(publicKey.getName())) {
            throw new IllegalStateException("KMS public key version does not match request");
        }
        if (publicKey.getAlgorithm() != CryptoKeyVersionAlgorithm.EC_SIGN_SECP256K1_SHA256) {
            throw new IllegalStateException("KMS key algorithm must be EC_SIGN_SECP256K1_SHA256");
        }
        if (!publicKey.hasPemCrc32C()) {
            throw new IllegalStateException("KMS public key checksum is missing");
        }
        if (crc32c(publicKey.getPemBytes().toByteArray()) != publicKey.getPemCrc32C().getValue()) {
            throw new IllegalStateException("KMS public key checksum does not match");
        }
        return publicKey;
    }

    private byte[] verifiedSignature(String keyVersionName, AsymmetricSignResponse response) {
        if (!keyVersionName.equals(response.getName())) {
            throw new IllegalStateException("KMS signature key version does not match request");
        }
        if (!response.getVerifiedDigestCrc32C()) {
            throw new IllegalStateException("KMS did not verify the transaction digest checksum");
        }
        if (!response.hasSignatureCrc32C()) {
            throw new IllegalStateException("KMS signature checksum is missing");
        }
        byte[] signature = response.getSignature().toByteArray();
        if (crc32c(signature) != response.getSignatureCrc32C().getValue()) {
            throw new IllegalStateException("KMS signature checksum does not match");
        }
        return signature;
    }

    static long crc32c(byte[] value) {
        CRC32C checksum = new CRC32C();
        checksum.update(value, 0, value.length);
        return checksum.getValue();
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
