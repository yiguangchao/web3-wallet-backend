package com.example.wallet.infrastructure.web3;

import com.example.wallet.common.exception.BizException;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

@Component
public class EthereumSignatureVerifier {

    public String recoverPersonalSignAddress(String message, String signature) {
        if (signature == null || !signature.matches("^(0x)?[0-9a-fA-F]{130}$")) {
            throw new BizException("wallet signature format is invalid");
        }

        byte[] signatureBytes = Numeric.hexStringToByteArray(signature);
        int recoveryId = Byte.toUnsignedInt(signatureBytes[64]);
        if (recoveryId == 0 || recoveryId == 1) {
            recoveryId += 27;
        }
        if (recoveryId != 27 && recoveryId != 28) {
            throw new BizException("wallet signature recovery id is invalid");
        }

        Sign.SignatureData signatureData = new Sign.SignatureData(
                (byte) recoveryId,
                Arrays.copyOfRange(signatureBytes, 0, 32),
                Arrays.copyOfRange(signatureBytes, 32, 64));
        try {
            String address = Keys.getAddress(Sign.signedPrefixedMessageToKey(
                    message.getBytes(StandardCharsets.UTF_8), signatureData));
            return ("0x" + address).toLowerCase(Locale.ROOT);
        } catch (SignatureException | RuntimeException ex) {
            throw new BizException("wallet signature is invalid");
        }
    }
}
