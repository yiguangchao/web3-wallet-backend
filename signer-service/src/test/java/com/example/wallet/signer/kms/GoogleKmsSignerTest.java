package com.example.wallet.signer.kms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.kms.v1.AsymmetricSignRequest;
import com.google.cloud.kms.v1.AsymmetricSignResponse;
import com.google.cloud.kms.v1.CryptoKeyVersion.CryptoKeyVersionAlgorithm;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.kms.v1.PublicKey;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int64Value;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.sec.SECObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

class GoogleKmsSignerTest {
    private static final String KEY_VERSION =
            "projects/test/locations/global/keyRings/wallet/cryptoKeys/hot-wallet/cryptoKeyVersions/1";
    private static final byte[] TRANSACTION_HASH = Numeric.hexStringToByteArray(
            "0x8f2a55949063d0b6f2f4f7f7fcb5d6dce44be330d834b16b0d477f902a67c2b5");
    private static final ECKeyPair KEY_PAIR = ECKeyPair.create(BigInteger.ONE);
    private static final String PUBLIC_KEY_PEM = publicKeyPem(KEY_PAIR.getPublicKey());
    private static final byte[] DER_SIGNATURE = derSignature(BigInteger.valueOf(7), BigInteger.valueOf(11));

    private KeyManagementServiceClient client;
    private GoogleKmsSigner signer;

    @BeforeEach
    void setUp() {
        client = mock(KeyManagementServiceClient.class);
        signer = new GoogleKmsSigner(client);
        when(client.getPublicKey(KEY_VERSION)).thenReturn(validPublicKey());
    }

    @Test
    void signsOnlyAfterVerifyingRequestAndResponseIntegrity() {
        when(client.asymmetricSign(any(AsymmetricSignRequest.class))).thenReturn(validSignResponse());

        KmsSignature result = signer.sign(KEY_VERSION, TRANSACTION_HASH);

        assertEquals(BigInteger.valueOf(7), result.r());
        assertEquals(BigInteger.valueOf(11), result.s());
        assertEquals(KEY_PAIR.getPublicKey(), result.publicKey());
        assertEquals("0x" + Keys.getAddress(KEY_PAIR.getPublicKey()), result.address());

        ArgumentCaptor<AsymmetricSignRequest> requestCaptor =
                ArgumentCaptor.forClass(AsymmetricSignRequest.class);
        verify(client).asymmetricSign(requestCaptor.capture());
        AsymmetricSignRequest request = requestCaptor.getValue();
        assertEquals(KEY_VERSION, request.getName());
        assertArrayEquals(TRANSACTION_HASH, request.getDigest().getSha256().toByteArray());
        assertTrue(request.hasDigestCrc32C());
        assertEquals(GoogleKmsSigner.crc32c(TRANSACTION_HASH), request.getDigestCrc32C().getValue());
    }

    @Test
    void verifiesPublicKeyIntegrityWhenDerivingAddress() {
        String address = signer.publicAddress(KEY_VERSION);

        assertEquals("0x" + Keys.getAddress(KEY_PAIR.getPublicKey()), address);
    }

    @Test
    void rejectsPublicKeyFromDifferentVersion() {
        when(client.getPublicKey(KEY_VERSION)).thenReturn(validPublicKey().toBuilder()
                .setName(KEY_VERSION.substring(0, KEY_VERSION.length() - 1) + "2")
                .build());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> signer.publicAddress(KEY_VERSION));

        assertEquals("KMS public key version does not match request", error.getMessage());
    }

    @Test
    void rejectsMissingPublicKeyChecksum() {
        when(client.getPublicKey(KEY_VERSION)).thenReturn(validPublicKey().toBuilder()
                .clearPemCrc32C()
                .build());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> signer.publicAddress(KEY_VERSION));

        assertEquals("KMS public key checksum is missing", error.getMessage());
    }

    @Test
    void rejectsCorruptedPublicKey() {
        when(client.getPublicKey(KEY_VERSION)).thenReturn(validPublicKey().toBuilder()
                .setPemCrc32C(Int64Value.of(GoogleKmsSigner.crc32c(PUBLIC_KEY_PEM.getBytes(StandardCharsets.UTF_8)) + 1))
                .build());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> signer.publicAddress(KEY_VERSION));

        assertEquals("KMS public key checksum does not match", error.getMessage());
    }

    @Test
    void rejectsSignatureWhenKmsDidNotVerifyDigestChecksum() {
        when(client.asymmetricSign(any(AsymmetricSignRequest.class))).thenReturn(validSignResponse().toBuilder()
                .setVerifiedDigestCrc32C(false)
                .build());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> signer.sign(KEY_VERSION, TRANSACTION_HASH));

        assertEquals("KMS did not verify the transaction digest checksum", error.getMessage());
    }

    @Test
    void rejectsSignatureFromDifferentKeyVersion() {
        when(client.asymmetricSign(any(AsymmetricSignRequest.class))).thenReturn(validSignResponse().toBuilder()
                .setName(KEY_VERSION.substring(0, KEY_VERSION.length() - 1) + "2")
                .build());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> signer.sign(KEY_VERSION, TRANSACTION_HASH));

        assertEquals("KMS signature key version does not match request", error.getMessage());
    }

    @Test
    void rejectsMissingSignatureChecksum() {
        when(client.asymmetricSign(any(AsymmetricSignRequest.class))).thenReturn(validSignResponse().toBuilder()
                .clearSignatureCrc32C()
                .build());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> signer.sign(KEY_VERSION, TRANSACTION_HASH));

        assertEquals("KMS signature checksum is missing", error.getMessage());
    }

    @Test
    void rejectsCorruptedSignature() {
        when(client.asymmetricSign(any(AsymmetricSignRequest.class))).thenReturn(validSignResponse().toBuilder()
                .setSignatureCrc32C(Int64Value.of(GoogleKmsSigner.crc32c(DER_SIGNATURE) + 1))
                .build());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> signer.sign(KEY_VERSION, TRANSACTION_HASH));

        assertEquals("KMS signature checksum does not match", error.getMessage());
    }

    @Test
    void rejectsDigestWithUnexpectedLengthBeforeCallingKms() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> signer.sign(KEY_VERSION, new byte[31]));

        assertEquals("transaction hash must be 32 bytes", error.getMessage());
    }

    private static PublicKey validPublicKey() {
        return PublicKey.newBuilder()
                .setName(KEY_VERSION)
                .setAlgorithm(CryptoKeyVersionAlgorithm.EC_SIGN_SECP256K1_SHA256)
                .setPem(PUBLIC_KEY_PEM)
                .setPemCrc32C(Int64Value.of(
                        GoogleKmsSigner.crc32c(PUBLIC_KEY_PEM.getBytes(StandardCharsets.UTF_8))))
                .build();
    }

    private static AsymmetricSignResponse validSignResponse() {
        return AsymmetricSignResponse.newBuilder()
                .setName(KEY_VERSION)
                .setSignature(ByteString.copyFrom(DER_SIGNATURE))
                .setSignatureCrc32C(Int64Value.of(GoogleKmsSigner.crc32c(DER_SIGNATURE)))
                .setVerifiedDigestCrc32C(true)
                .build();
    }

    private static String publicKeyPem(BigInteger publicKey) {
        try {
            byte[] point = new byte[65];
            point[0] = 0x04;
            System.arraycopy(Numeric.toBytesPadded(publicKey, 64), 0, point, 1, 64);
            AlgorithmIdentifier algorithm = new AlgorithmIdentifier(
                    X9ObjectIdentifiers.id_ecPublicKey, SECObjectIdentifiers.secp256k1);
            byte[] der = new SubjectPublicKeyInfo(algorithm, point).getEncoded();
            String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
            return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n";
        } catch (Exception ex) {
            throw new IllegalStateException("cannot create test public key", ex);
        }
    }

    private static byte[] derSignature(BigInteger r, BigInteger s) {
        try {
            return new DERSequence(new ASN1Encodable[]{new ASN1Integer(r), new ASN1Integer(s)}).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("cannot create test signature", ex);
        }
    }
}
