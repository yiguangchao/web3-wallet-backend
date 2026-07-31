package com.example.wallet.signer.api;

import com.example.wallet.signer.core.KeyManagementService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/key-changes")
public class KeyManagementController {
    private final KeyManagementService service;
    public KeyManagementController(KeyManagementService service) { this.service = service; }
    @PostMapping public Map<String, Long> propose(@Valid @RequestBody KeyChangeRequest request) {
        return Map.of("changeId", service.propose(request));
    }
    @PostMapping("/{changeId}/approve") public Map<String, String> approve(@PathVariable long changeId) {
        service.approve(changeId); return Map.of("status", "APPROVED");
    }
    @PostMapping("/emergency-stop") public Map<String, String> emergencyStop(
            @RequestBody Map<String, String> request) {
        service.emergencyStop(request.get("reason")); return Map.of("status", "STOPPED");
    }
}
