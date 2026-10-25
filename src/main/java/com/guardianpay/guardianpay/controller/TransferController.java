package com.guardianpay.guardianpay.controller;

import com.guardianpay.guardianpay.dto.TransferRequest;
import com.guardianpay.guardianpay.dto.TransferResponse;
import com.guardianpay.guardianpay.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest req,
                                                     Authentication authentication) {
        TransferResponse response = transferService.transfer(authentication.getName(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}