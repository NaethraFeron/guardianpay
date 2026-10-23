package com.guardianpay.guardianpay.dto;

import com.guardianpay.guardianpay.model.AccountStatus;
import java.math.BigDecimal;

public record AccountResponse(Long accountId, BigDecimal balance, AccountStatus status) {}