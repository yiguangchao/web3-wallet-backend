package com.example.wallet.module.asset.controller;

import com.example.wallet.common.exception.BizException;
import com.example.wallet.common.result.Result;
import com.example.wallet.common.utils.SecurityUtils;
import com.example.wallet.module.asset.dto.AssetFlowPage;
import com.example.wallet.module.asset.entity.AssetAccount;
import com.example.wallet.module.asset.entity.AssetFlow;
import com.example.wallet.module.asset.service.AssetService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/flows/page")
    public ResponseEntity<Result<AssetFlowPage>> listFlowPage(
            @RequestParam(required = false) String assetId,
            @RequestParam(required = false) String beforeId,
            @RequestParam(defaultValue = "20") String limit) {
        Long userId = SecurityUtils.getCurrentUserId();
        Long parsedAssetId = parsePositiveId(assetId);
        Long parsedBeforeId = parsePositiveId(beforeId);
        Long parsedLimit = parsePositiveId(limit);
        if (parsedLimit == null || parsedLimit > 100) {
            throw new BizException(400, "limit must be between 1 and 100");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Result.success(assetService.listFlowPage(
                        userId, parsedAssetId, parsedBeforeId, parsedLimit.intValue())));
    }

    private Long parsePositiveId(String value) {
        if (value == null) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Invalid or overflowing input is a client error.
        }
        throw new BizException(400, "pagination parameters must be positive integers");
    }
}
