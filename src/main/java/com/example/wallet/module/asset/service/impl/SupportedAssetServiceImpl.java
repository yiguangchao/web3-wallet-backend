package com.example.wallet.module.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.wallet.common.exception.BizException;
import com.example.wallet.module.asset.entity.SupportedAsset;
import com.example.wallet.module.asset.entity.SupportedAssetType;
import com.example.wallet.module.asset.mapper.SupportedAssetMapper;
import com.example.wallet.module.asset.service.SupportedAssetService;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SupportedAssetServiceImpl implements SupportedAssetService {

    private static final int ACTIVE = 1;

    private final SupportedAssetMapper assetMapper;

    public SupportedAssetServiceImpl(SupportedAssetMapper assetMapper) {
        this.assetMapper = assetMapper;
    }

    @Override
    public SupportedAsset getRequiredByAssetCode(String assetCode) {
        String normalized = StringUtils.hasText(assetCode)
                ? assetCode.trim().toUpperCase(Locale.ROOT) : "";
        SupportedAsset asset = assetMapper.selectOne(new LambdaQueryWrapper<SupportedAsset>()
                .eq(SupportedAsset::getAssetCode, normalized));
        return requireActive(asset);
    }

    @Override
    public SupportedAsset getRequiredById(Long assetId) {
        SupportedAsset asset = assetId == null ? null : assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BizException("supported asset not found");
        }
        return asset;
    }

    @Override
    public SupportedAsset getRequiredByTokenAddress(long chainId, String tokenAddress) {
        if (tokenAddress == null || !tokenAddress.matches("^0x[0-9a-fA-F]{40}$")) {
            throw new BizException("token address is invalid");
        }
        SupportedAsset asset = assetMapper.selectOne(new LambdaQueryWrapper<SupportedAsset>()
                .eq(SupportedAsset::getChainId, chainId)
                .eq(SupportedAsset::getTokenAddress, tokenAddress.toLowerCase(Locale.ROOT))
                .eq(SupportedAsset::getAssetType, SupportedAssetType.ERC20.name()));
        return requireActive(asset);
    }

    @Override
    public SupportedAsset getRequiredNativeAsset(long chainId) {
        SupportedAsset asset = assetMapper.selectOne(new LambdaQueryWrapper<SupportedAsset>()
                .eq(SupportedAsset::getChainId, chainId)
                .eq(SupportedAsset::getAssetType, SupportedAssetType.NATIVE.name()));
        return requireActive(asset);
    }

    @Override
    public SupportedAsset getRequiredDepositAsset(Long assetId) {
        SupportedAsset asset = requireActive(getRequiredById(assetId));
        if (!Boolean.TRUE.equals(asset.getDepositEnabled())) {
            throw new BizException("asset deposit is disabled");
        }
        return asset;
    }

    @Override
    public SupportedAsset getRequiredWithdrawAsset(String assetCode) {
        SupportedAsset asset = getRequiredByAssetCode(assetCode);
        if (!Boolean.TRUE.equals(asset.getWithdrawEnabled())) {
            throw new BizException("asset withdrawal is disabled");
        }
        return asset;
    }

    @Override
    public List<SupportedAsset> listDepositEnabledErc20(long chainId) {
        return assetMapper.selectList(new LambdaQueryWrapper<SupportedAsset>()
                .eq(SupportedAsset::getChainId, chainId)
                .eq(SupportedAsset::getAssetType, SupportedAssetType.ERC20.name())
                .eq(SupportedAsset::getDepositEnabled, true)
                .eq(SupportedAsset::getStatus, ACTIVE));
    }

    private SupportedAsset requireActive(SupportedAsset asset) {
        if (asset == null) {
            throw new BizException("supported asset not found");
        }
        if (!Integer.valueOf(ACTIVE).equals(asset.getStatus())) {
            throw new BizException("supported asset is disabled");
        }
        return asset;
    }
}
