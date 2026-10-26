package com.guardianpay.guardianpay.controller;

import com.guardianpay.guardianpay.dto.TransferView;
import com.guardianpay.guardianpay.model.AuditLog;
import com.guardianpay.guardianpay.repository.AuditLogRepository;
import com.guardianpay.guardianpay.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TransferService transferService;
    private final AuditLogRepository auditLogRepository;

    @GetMapping("/review")
    public List<TransferView> reviewQueue() {
        return transferService.reviewQueue();
    }

    @PostMapping("/transfers/{id}/approve")
    public TransferView approve(@PathVariable Long id, Authentication authentication) {
        return transferService.adminDecision(authentication.getName(), id, true);
    }

    @PostMapping("/transfers/{id}/reject")
    public TransferView reject(@PathVariable Long id, Authentication authentication) {
        return transferService.adminDecision(authentication.getName(), id, false);
    }

    @GetMapping("/audit")
    public List<AuditLog> audit() {
        return auditLogRepository.findTop50ByOrderByCreatedAtDesc();
    }
}