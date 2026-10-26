package com.guardianpay.guardianpay.dto;

import com.guardianpay.guardianpay.model.TransferStatus;
import java.math.BigDecimal;
import java.util.List;

public record TransferResponse(Long transferId, TransferStatus status, BigDecimal amount,
                               Long toAccountId, BigDecimal yourNewBalance,
                               int riskScore, List<String> reasons, String message) {}