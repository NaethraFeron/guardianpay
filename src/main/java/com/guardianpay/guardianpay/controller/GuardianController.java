package com.guardianpay.guardianpay.controller;

import com.guardianpay.guardianpay.dto.GuardianRequest;
import com.guardianpay.guardianpay.dto.TransferView;
import com.guardianpay.guardianpay.service.GuardianService;
import com.guardianpay.guardianpay.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/guardian")
@RequiredArgsConstructor
public class GuardianController {

    private final GuardianService guardianService;
    private final TransferService transferService;

    @PostMapping("/set")
    public ResponseEntity<Map<String, String>> setGuardian(@Valid @RequestBody GuardianRequest req,
                                                           Authentication authentication) {
        guardianService.setGuardian(authentication.getName(), req.guardianEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Guardian set"));
    }

    @GetMapping("/pending")
    public List<TransferView> pending(Authentication authentication) {
        return guardianService.pendingFor(authentication.getName());
    }

    @PostMapping("/transfers/{id}/approve")
    public TransferView approve(@PathVariable Long id, Authentication authentication) {
        return transferService.guardianDecision(authentication.getName(), id, true);
    }

    @PostMapping("/transfers/{id}/reject")
    public TransferView reject(@PathVariable Long id, Authentication authentication) {
        return transferService.guardianDecision(authentication.getName(), id, false);
    }
}