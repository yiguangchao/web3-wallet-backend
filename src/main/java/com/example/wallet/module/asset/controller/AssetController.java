package com.example.wallet.module.asset.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.common.utils.SecurityUtils;
import com.example.wallet.module.asset.entity.AssetAccount;
import com.example.wallet.module.asset.entity.AssetFlow;
import com.example.wallet.module.asset.service.AssetService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/asset")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @GetMapping("/accounts")
    public Result<List<AssetAccount>> listAccounts() {
        return Result.success(assetService.listAccounts(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/flows")
    public Result<List<AssetFlow>> listFlows() {
        return Result.success(assetService.listFlows(SecurityUtils.getCurrentUserId()));
    }
}
