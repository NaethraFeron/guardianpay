package com.guardianpay.guardianpay.repository;

import com.guardianpay.guardianpay.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {}