package com.example.wallet.module.wallet.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.wallet.common.exception.BizException;
import com.example.wallet.infrastructure.web3.EthereumSignatureVerifier;
import com.example.wallet.infrastructure.web3.Web3Service;
import com.example.wallet.module.wallet.config.WalletSignatureProperties;
import com.example.wallet.module.wallet.dto.CreateWalletChallengeRequest;
import com.example.wallet.module.wallet.dto.ExternalWalletAddressResponse;
import com.example.wallet.module.wallet.dto.VerifyWalletSignatureRequest;
import com.example.wallet.module.wallet.dto.WalletChallengeResponse;
import com.example.wallet.module.wallet.entity.WalletAddress;
import com.example.wallet.module.wallet.entity.WalletSignChallenge;
import com.example.wallet.module.wallet.mapper.WalletAddressMapper;
import com.example.wallet.module.wallet.mapper.WalletSignChallengeMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

@ExtendWith(MockitoExtension.class)
class ExternalWalletServiceImplTest {

    @Mock
    private WalletSignChallengeMapper challengeMapper;
    @Mock
    private WalletAddressMapper addressMapper;
    @Mock
    private Web3Service web3Service;

    private ExternalWalletServiceImpl service;

    @BeforeEach
    void setUp() {
        WalletSignatureProperties properties = new WalletSignatureProperties();
        properties.setChallengeTtl(300_000L);
        service = new ExternalWalletServiceImpl(
                challengeMapper, addressMapper, web3Service, new EthereumSignatureVerifier(), properties);
    }

    @Test
    void shouldCreateUserAndAddressBoundChallenge() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        String checksumInsensitiveAddress = "0x" + Keys.getAddress(keyPair).toUpperCase(Locale.ROOT);
        CreateWalletChallengeRequest request = challengeRequest(checksumInsensitiveAddress);
        when(web3Service.isValidAddress(checksumInsensitiveAddress)).thenReturn(true);
        when(challengeMapper.insert(any(WalletSignChallenge.class))).thenAnswer(invocation -> {
            WalletSignChallenge challenge = invocation.getArgument(0);
            challenge.setId(10L);
            return 1;
        });

        WalletChallengeResponse response = service.createChallenge(1001L, request);

        assertThat(response.challengeId()).isEqualTo(10L);
        assertThat(response.address()).isEqualTo(checksumInsensitiveAddress.toLowerCase(Locale.ROOT));
        assertThat(response.nonce()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(response.message())
                .contains("User ID: 1001")
                .contains("Chain: ETH_SEPOLIA")
                .contains("Address: " + response.address())
                .contains("Nonce: " + response.nonce())
                .contains("Issued At: " + response.issuedAt())
                .contains("Expires At: " + response.expireTime());
    }

    @Test
    void shouldVerifyPersonalSignAndBindAddressOnce() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        WalletSignChallenge challenge = validChallenge(20L, 1001L, keyPair);
        when(challengeMapper.selectByIdForUpdate(20L)).thenReturn(challenge);
        when(addressMapper.insert(any(WalletAddress.class))).thenAnswer(invocation -> {
            WalletAddress address = invocation.getArgument(0);
            address.setId(30L);
            return 1;
        });
        when(challengeMapper.consumeIfValid(any(), any(), any())).thenReturn(1);
        VerifyWalletSignatureRequest request = verifyRequest(20L, sign(challenge.getMessage(), keyPair));

        ExternalWalletAddressResponse response = service.verifyAndBind(1001L, request);

        assertThat(response.id()).isEqualTo(30L);
        assertThat(response.address()).isEqualTo(challenge.getAddress());
        ArgumentCaptor<WalletAddress> addressCaptor = ArgumentCaptor.forClass(WalletAddress.class);
        verify(addressMapper).insert(addressCaptor.capture());
        assertThat(addressCaptor.getValue())
                .extracting(WalletAddress::getUserId, WalletAddress::getChain,
                        WalletAddress::getAddressType, WalletAddress::getStatus)
                .containsExactly(1001L, "ETH_SEPOLIA", "EXTERNAL", 1);
        assertThat(addressCaptor.getValue().getVerifiedAt()).isNotNull();
        assertThat(challenge.getUsed()).isTrue();
        assertThat(challenge.getUsedAt()).isNotNull();
    }

    @Test
    void shouldRejectSignatureFromDifferentWallet() throws Exception {
        ECKeyPair ownerKey = Keys.createEcKeyPair();
        ECKeyPair attackerKey = Keys.createEcKeyPair();
        WalletSignChallenge challenge = validChallenge(20L, 1001L, ownerKey);
        when(challengeMapper.selectByIdForUpdate(20L)).thenReturn(challenge);
        VerifyWalletSignatureRequest request = verifyRequest(20L, sign(challenge.getMessage(), attackerKey));

        assertThatThrownBy(() -> service.verifyAndBind(1001L, request))
                .isInstanceOf(BizException.class)
                .hasMessage("wallet signature does not match the requested address");
        verify(addressMapper, never()).insert(any(WalletAddress.class));
        verify(challengeMapper, never()).updateById(any(WalletSignChallenge.class));
    }

    @Test
    void shouldRejectExpiredChallengeBeforeSignatureVerification() throws Exception {
        WalletSignChallenge challenge = validChallenge(20L, 1001L, Keys.createEcKeyPair());
        challenge.setExpireTime(LocalDateTime.now().minusSeconds(1));
        when(challengeMapper.selectByIdForUpdate(20L)).thenReturn(challenge);

        assertThatThrownBy(() -> service.verifyAndBind(1001L, verifyRequest(20L, "0x" + "00".repeat(65))))
                .isInstanceOf(BizException.class)
                .hasMessage("wallet signature challenge has expired");
        verify(addressMapper, never()).insert(any(WalletAddress.class));
    }

    @Test
    void shouldRejectUsedChallengeReplay() throws Exception {
        WalletSignChallenge challenge = validChallenge(20L, 1001L, Keys.createEcKeyPair());
        challenge.setUsed(true);
        when(challengeMapper.selectByIdForUpdate(20L)).thenReturn(challenge);

        assertThatThrownBy(() -> service.verifyAndBind(1001L, verifyRequest(20L, "0x" + "00".repeat(65))))
                .isInstanceOf(BizException.class)
                .hasMessage("wallet signature challenge has already been used");
        verify(addressMapper, never()).insert(any(WalletAddress.class));
    }

    @Test
    void shouldRejectChallengeCreatedByAnotherUser() throws Exception {
        WalletSignChallenge challenge = validChallenge(20L, 1001L, Keys.createEcKeyPair());
        when(challengeMapper.selectByIdForUpdate(20L)).thenReturn(challenge);

        assertThatThrownBy(() -> service.verifyAndBind(2002L, verifyRequest(20L, "0x" + "00".repeat(65))))
                .isInstanceOf(BizException.class)
                .hasMessage("wallet signature challenge not found");
        verify(addressMapper, never()).insert(any(WalletAddress.class));
        verify(challengeMapper, never()).updateById(any(WalletSignChallenge.class));
    }

    @Test
    void shouldRejectAddressAlreadyOwnedByAnotherUser() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        WalletSignChallenge challenge = validChallenge(20L, 1001L, keyPair);
        WalletAddress existing = new WalletAddress();
        existing.setUserId(2002L);
        when(challengeMapper.selectByIdForUpdate(20L)).thenReturn(challenge);
        when(addressMapper.selectByChainAndAddress(challenge.getChain(), challenge.getAddress()))
                .thenReturn(existing);

        assertThatThrownBy(() -> service.verifyAndBind(
                1001L, verifyRequest(20L, sign(challenge.getMessage(), keyPair))))
                .isInstanceOf(BizException.class)
                .hasMessage("wallet address is already bound to another user");
        verify(addressMapper, never()).insert(any(WalletAddress.class));
        verify(challengeMapper, never()).updateById(any(WalletSignChallenge.class));
    }

    @Test
    void shouldUpgradeLegacyAddressOwnedBySameUserToVerified() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        WalletSignChallenge challenge = validChallenge(20L, 1001L, keyPair);
        WalletAddress legacyAddress = new WalletAddress();
        legacyAddress.setId(30L);
        legacyAddress.setUserId(1001L);
        legacyAddress.setChain(challenge.getChain());
        legacyAddress.setAddress(challenge.getAddress());
        when(challengeMapper.selectByIdForUpdate(20L)).thenReturn(challenge);
        when(addressMapper.selectByChainAndAddress(challenge.getChain(), challenge.getAddress()))
                .thenReturn(legacyAddress);
        when(addressMapper.updateById(legacyAddress)).thenReturn(1);
        when(challengeMapper.consumeIfValid(any(), any(), any())).thenReturn(1);

        ExternalWalletAddressResponse response = service.verifyAndBind(
                1001L, verifyRequest(20L, sign(challenge.getMessage(), keyPair)));

        assertThat(response.id()).isEqualTo(30L);
        assertThat(legacyAddress.getVerifiedAt()).isNotNull();
        assertThat(legacyAddress.getAddressType()).isEqualTo("EXTERNAL");
        assertThat(legacyAddress.getStatus()).isEqualTo(1);
        verify(addressMapper, never()).insert(any(WalletAddress.class));
    }

    @Test
    void shouldRejectWhenAtomicChallengeConsumptionLosesTheRace() throws Exception {
        ECKeyPair keyPair = Keys.createEcKeyPair();
        WalletSignChallenge challenge = validChallenge(20L, 1001L, keyPair);
        when(challengeMapper.selectByIdForUpdate(20L)).thenReturn(challenge);
        when(addressMapper.insert(any(WalletAddress.class))).thenAnswer(invocation -> {
            WalletAddress address = invocation.getArgument(0);
            address.setId(30L);
            return 1;
        });
        when(challengeMapper.consumeIfValid(any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.verifyAndBind(
                1001L, verifyRequest(20L, sign(challenge.getMessage(), keyPair))))
                .isInstanceOf(BizException.class)
                .hasMessage("wallet signature challenge has already been used or expired");
    }

    private CreateWalletChallengeRequest challengeRequest(String address) {
        CreateWalletChallengeRequest request = new CreateWalletChallengeRequest();
        request.setChain("eth_sepolia");
        request.setAddress(address);
        return request;
    }

    private WalletSignChallenge validChallenge(Long id, Long userId, ECKeyPair keyPair) {
        WalletSignChallenge challenge = new WalletSignChallenge();
        challenge.setId(id);
        challenge.setUserId(userId);
        challenge.setChain("ETH_SEPOLIA");
        challenge.setAddress("0x" + Keys.getAddress(keyPair));
        challenge.setNonce("ab".repeat(32));
        challenge.setMessage("ownership challenge " + id);
        challenge.setExpireTime(LocalDateTime.now().plusMinutes(5));
        challenge.setUsed(false);
        challenge.setCreatedAt(LocalDateTime.now());
        return challenge;
    }

    private VerifyWalletSignatureRequest verifyRequest(Long challengeId, String signature) {
        VerifyWalletSignatureRequest request = new VerifyWalletSignatureRequest();
        request.setChallengeId(challengeId);
        request.setSignature(signature);
        return request;
    }

    private String sign(String message, ECKeyPair keyPair) {
        Sign.SignatureData signature = Sign.signPrefixedMessage(
                message.getBytes(StandardCharsets.UTF_8), keyPair);
        return "0x"
                + Numeric.toHexStringNoPrefix(signature.getR())
                + Numeric.toHexStringNoPrefix(signature.getS())
                + Numeric.toHexStringNoPrefix(signature.getV());
    }
}
