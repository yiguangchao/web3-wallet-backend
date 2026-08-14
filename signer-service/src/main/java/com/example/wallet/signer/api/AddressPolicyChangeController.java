package com.example.wallet.signer.api;

import com.example.wallet.signer.core.AddressPolicyChangeService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/address-policy-changes")
public class AddressPolicyChangeController {
    private final AddressPolicyChangeService service;

    public AddressPolicyChangeController(AddressPolicyChangeService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Long> propose(@Valid @RequestBody AddressPolicyChangeRequest request) {
        return Map.of("changeId", service.propose(request));
    }

    @PostMapping("/{changeId}/approve")
    public Map<String, String> approve(@PathVariable long changeId) {
        service.approve(changeId);
        return Map.of("status", "APPROVED");
    }
}
