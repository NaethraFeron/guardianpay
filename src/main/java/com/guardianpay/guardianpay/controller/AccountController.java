package com.guardianpay.guardianpay.controller;

import com.guardianpay.guardianpay.dto.AccountResponse;
import com.guardianpay.guardianpay.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;

    @GetMapping("/me")
    public ResponseEntity<AccountResponse> myAccount(Authentication authentication) {
        return accountRepository.findByUserEmail(authentication.getName())
                .map(a -> ResponseEntity.ok(
                        new AccountResponse(a.getId(), a.getBalance(), a.getStatus())))
                .orElse(ResponseEntity.notFound().build());
    }
}