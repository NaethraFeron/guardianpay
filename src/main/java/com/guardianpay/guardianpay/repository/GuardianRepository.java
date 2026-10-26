package com.guardianpay.guardianpay.repository;

import com.guardianpay.guardianpay.model.Guardian;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {
    Optional<Guardian> findByProtectedUserId(Long protectedUserId);

    List<Guardian> findByGuardianUserEmail(String email);
}