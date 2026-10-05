package com.example.wallet.module.asset.controller;

import com.example.wallet.common.result.Result;
import com.example.wallet.module.asset.dto.SupportedAssetResponse;
import com.example.wallet.module.asset.service.SupportedAssetService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/asset")
public class SupportedAssetController {

    private final SupportedAssetService supportedAssetService;

    public SupportedAssetController(SupportedAssetService supportedAssetService) {
        this.supportedAssetService = supportedAssetService;
    }

    @GetMapping("/supported")
    public ResponseEntity<Result<List<SupportedAssetResponse>>> listSupportedAssets() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Result.success(supportedAssetService.listSupportedAssets()));
    }
}
