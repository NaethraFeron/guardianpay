package com.guardianpay.guardianpay.repository;

import com.guardianpay.guardianpay.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByUserEmail(String email);
}