package com.example.wallet.signer.api;

import com.example.wallet.signer.core.StaleSigningRequest;
import com.example.wallet.signer.core.StaleSigningRequestService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/signing-resolutions")
public class StaleSigningRequestController {
    private final StaleSigningRequestService service;

    public StaleSigningRequestController(StaleSigningRequestService service) {
        this.service = service;
    }

    @GetMapping("/stale")
    public List<StaleSigningRequest> listStale() {
        return service.list();
    }
}
