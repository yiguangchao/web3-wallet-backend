package com.example.wallet.module.wallet.service;

import com.example.wallet.module.wallet.dto.CreateWalletChallengeRequest;
import com.example.wallet.module.wallet.dto.ExternalWalletAddressResponse;
import com.example.wallet.module.wallet.dto.VerifyWalletSignatureRequest;
import com.example.wallet.module.wallet.dto.WalletChallengeResponse;
import java.util.List;

public interface ExternalWalletService {

    WalletChallengeResponse createChallenge(Long userId, CreateWalletChallengeRequest request);

    ExternalWalletAddressResponse verifyAndBind(Long userId, VerifyWalletSignatureRequest request);

    List<ExternalWalletAddressResponse> listAddresses(Long userId);
}
