package com.guardianpay.guardianpay.dto;

import com.guardianpay.guardianpay.model.TransferStatus;
import java.math.BigDecimal;

public record TransferResponse(Long transferId, TransferStatus status, BigDecimal amount,
                               Long toAccountId, BigDecimal yourNewBalance) {}