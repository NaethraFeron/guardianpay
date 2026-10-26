package com.guardianpay.guardianpay.dto;

import com.guardianpay.guardianpay.model.Transfer;
import com.guardianpay.guardianpay.model.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record TransferView(Long id, Long fromAccountId, Long toAccountId, BigDecimal amount,
                           TransferStatus status, int riskScore, String reasons,
                           Instant createdAt, Instant releaseAt, Instant deadlineAt) {

    public static TransferView from(Transfer t) {
        return new TransferView(t.getId(), t.getFromAccount().getId(), t.getToAccount().getId(),
                t.getAmount(), t.getStatus(), t.getRiskScore(), t.getReasons(),
                t.getCreatedAt(), t.getReleaseAt(), t.getDeadlineAt());
    }
}