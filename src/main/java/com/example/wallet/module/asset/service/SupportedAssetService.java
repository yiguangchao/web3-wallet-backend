package com.example.wallet.module.asset.service;

import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.dto.SupportedAssetResponse;
import java.util.List;

public interface SupportedAssetService {

    List<SupportedAssetResponse> listSupportedAssets();

    SupportedAsset getRequiredByAssetCode(String assetCode);

    SupportedAsset getRequiredById(Long assetId);

    SupportedAsset getRequiredByTokenAddress(long chainId, String tokenAddress);

    SupportedAsset getRequiredNativeAsset(long chainId);

    SupportedAsset getRequiredDepositAsset(Long assetId);

    SupportedAsset getRequiredWithdrawAsset(String assetCode);

    List<SupportedAsset> listDepositEnabledErc20(long chainId);
}
