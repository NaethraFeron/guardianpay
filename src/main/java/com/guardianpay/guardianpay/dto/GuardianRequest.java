package com.guardianpay.guardianpay.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record GuardianRequest(@NotBlank @Email String guardianEmail) {}