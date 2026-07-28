package com.example.wallet.module.wallet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.example.wallet.module.wallet.service.ExternalWalletService;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExternalWalletServiceImpl implements ExternalWalletService {

    private static final String ADDRESS_TYPE_EXTERNAL = "EXTERNAL";
    private static final int ACTIVE_STATUS = 1;

    private final WalletSignChallengeMapper challengeMapper;
    private final WalletAddressMapper addressMapper;
    private final Web3Service web3Service;
    private final EthereumSignatureVerifier signatureVerifier;
    private final WalletSignatureProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public ExternalWalletServiceImpl(WalletSignChallengeMapper challengeMapper,
                                     WalletAddressMapper addressMapper,
                                     Web3Service web3Service,
                                     EthereumSignatureVerifier signatureVerifier,
                                     WalletSignatureProperties properties) {
        this.challengeMapper = challengeMapper;
        this.addressMapper = addressMapper;
        this.web3Service = web3Service;
        this.signatureVerifier = signatureVerifier;
        this.properties = properties;
    }

    @Override
    public WalletChallengeResponse createChallenge(Long userId, CreateWalletChallengeRequest request) {
        String chain = normalizeChain(request.getChain());
        String address = normalizeAddress(request.getAddress());
        WalletAddress existing = addressMapper.selectByChainAndAddress(chain, address);
        if (existing != null
                && (!userId.equals(existing.getUserId()) || existing.getVerifiedAt() != null)) {
            throw new BizException("wallet address is already bound");
        }
        if (properties.getChallengeTtl() < 1_000L) {
            throw new BizException("wallet signature challenge TTL is invalid");
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime expireTime = now.plus(Duration.ofMillis(properties.getChallengeTtl()))
                .truncatedTo(ChronoUnit.SECONDS);
        String nonce = generateNonce();
        String message = buildMessage(userId, chain, address, nonce, now, expireTime);

        WalletSignChallenge challenge = new WalletSignChallenge();
        challenge.setUserId(userId);
        challenge.setChain(chain);
        challenge.setAddress(address);
        challenge.setNonce(nonce);
        challenge.setMessage(message);
        challenge.setExpireTime(expireTime);
        challenge.setUsed(false);
        challenge.setCreatedAt(now);
        if (challengeMapper.insert(challenge) != 1) {
            throw new BizException("wallet signature challenge creation failed");
        }
        return toChallengeResponse(challenge);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExternalWalletAddressResponse verifyAndBind(Long userId, VerifyWalletSignatureRequest request) {
        WalletSignChallenge challenge = challengeMapper.selectByIdForUpdate(request.getChallengeId());
        if (challenge == null || !userId.equals(challenge.getUserId())) {
            throw new BizException("wallet signature challenge not found");
        }
        if (Boolean.TRUE.equals(challenge.getUsed())) {
            throw new BizException("wallet signature challenge has already been used");
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        if (!challenge.getExpireTime().isAfter(now)) {
            throw new BizException("wallet signature challenge has expired");
        }
        String recoveredAddress = signatureVerifier.recoverPersonalSignAddress(
                challenge.getMessage(), request.getSignature());
        if (!challenge.getAddress().equals(recoveredAddress)) {
            throw new BizException("wallet signature does not match the requested address");
        }

        WalletAddress address = addressMapper.selectByChainAndAddress(challenge.getChain(), challenge.getAddress());
        if (address != null && !userId.equals(address.getUserId())) {
            throw new BizException("wallet address is already bound to another user");
        }
        if (address == null) {
            address = createAddress(userId, challenge, now);
            try {
                addressMapper.insert(address);
            } catch (DuplicateKeyException ex) {
                throw new BizException("wallet address is already bound to another user");
            }
        } else {
            address.setAddressType(ADDRESS_TYPE_EXTERNAL);
            address.setStatus(ACTIVE_STATUS);
            address.setVerifiedAt(now);
            address.setUpdatedAt(now);
            if (addressMapper.updateById(address) != 1) {
                throw new BizException("wallet address verification update failed");
            }
        }

        if (challengeMapper.consumeIfValid(challenge.getId(), userId, now) != 1) {
            throw new BizException("wallet signature challenge has already been used or expired");
        }
        challenge.setUsed(true);
        challenge.setUsedAt(now);
        return toAddressResponse(address);
    }

    @Override
    public List<ExternalWalletAddressResponse> listAddresses(Long userId) {
        return addressMapper.selectList(new LambdaQueryWrapper<WalletAddress>()
                        .eq(WalletAddress::getUserId, userId)
                        .eq(WalletAddress::getAddressType, ADDRESS_TYPE_EXTERNAL)
                        .eq(WalletAddress::getStatus, ACTIVE_STATUS)
                        .isNotNull(WalletAddress::getVerifiedAt)
                        .orderByDesc(WalletAddress::getVerifiedAt))
                .stream()
                .map(this::toAddressResponse)
                .toList();
    }

    private WalletAddress createAddress(Long userId, WalletSignChallenge challenge, LocalDateTime now) {
        WalletAddress address = new WalletAddress();
        address.setUserId(userId);
        address.setChain(challenge.getChain());
        address.setAddress(challenge.getAddress());
        address.setAddressType(ADDRESS_TYPE_EXTERNAL);
        address.setStatus(ACTIVE_STATUS);
        address.setVerifiedAt(now);
        address.setCreatedAt(now);
        address.setUpdatedAt(now);
        return address;
    }

    private String normalizeChain(String chain) {
        String normalized = chain == null ? "" : chain.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("^[A-Z0-9_]{2,32}$")) {
            throw new BizException("wallet chain is invalid");
        }
        return normalized;
    }

    private String normalizeAddress(String address) {
        if (!web3Service.isValidAddress(address)) {
            throw new BizException("wallet address is invalid");
        }
        return address.toLowerCase(Locale.ROOT);
    }

    private String generateNonce() {
        byte[] nonce = new byte[32];
        secureRandom.nextBytes(nonce);
        return HexFormat.of().formatHex(nonce);
    }

    private String buildMessage(Long userId, String chain, String address, String nonce,
                                LocalDateTime issuedAt,
                                LocalDateTime expireTime) {
        return """
                Web3 Wallet address ownership verification

                User ID: %d
                Chain: %s
                Address: %s
                Nonce: %s
                Issued At: %s
                Expires At: %s

                Sign this message only to bind this address to web3-wallet-backend.
                """.formatted(userId, chain, address, nonce, issuedAt, expireTime).stripTrailing();
    }

    private WalletChallengeResponse toChallengeResponse(WalletSignChallenge challenge) {
        return new WalletChallengeResponse(
                challenge.getId(), challenge.getChain(), challenge.getAddress(), challenge.getNonce(),
                challenge.getMessage(), challenge.getCreatedAt(), challenge.getExpireTime());
    }

    private ExternalWalletAddressResponse toAddressResponse(WalletAddress address) {
        return new ExternalWalletAddressResponse(
                address.getId(), address.getChain(), address.getAddress(), address.getVerifiedAt());
    }
}
