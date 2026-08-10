package com.example.wallet.signer.api;

import com.example.wallet.signer.core.IdempotencyResolutionService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/signing-resolutions")
public class IdempotencyResolutionController {
    private final IdempotencyResolutionService service;

    public IdempotencyResolutionController(IdempotencyResolutionService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Long> propose(@Valid @RequestBody IdempotencyResolutionRequest request) {
        return Map.of("resolutionId", service.propose(request));
    }

    @PostMapping("/{resolutionId}/approve")
    public Map<String, String> approve(@PathVariable long resolutionId) {
        service.approve(resolutionId);
        return Map.of("status", "APPROVED");
    }
}
