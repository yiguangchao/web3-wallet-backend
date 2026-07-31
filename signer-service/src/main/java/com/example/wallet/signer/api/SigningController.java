package com.example.wallet.signer.api;

import com.example.wallet.signer.core.SigningApiService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sign")
public class SigningController {
    private final SigningApiService service;
    public SigningController(SigningApiService service) { this.service = service; }

    @PostMapping("/ethereum-transaction")
    public SignResponse sign(@RequestHeader("Idempotency-Key") String idempotencyKey,
                             @Valid @RequestBody SignRequest request) {
        return service.sign(idempotencyKey, request);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<java.util.Map<String, String>> error(RuntimeException ex) {
        int status = ex instanceof IllegalArgumentException ? 400 : 409;
        return ResponseEntity.status(status).body(java.util.Map.of("error", ex.getMessage()));
    }
}
