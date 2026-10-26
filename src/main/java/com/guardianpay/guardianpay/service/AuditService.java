package com.guardianpay.guardianpay.service;

import com.guardianpay.guardianpay.model.AuditLog;
import com.guardianpay.guardianpay.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(String actor, String action, String details) {
        AuditLog entry = new AuditLog();
        entry.setActor(actor);
        entry.setAction(action);
        entry.setDetails(details);
        auditLogRepository.save(entry);
    }
}