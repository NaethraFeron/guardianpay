package com.guardianpay.guardianpay.repository;

import com.guardianpay.guardianpay.model.Transfer;
import com.guardianpay.guardianpay.model.TransferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    boolean existsByFromAccountIdAndToAccountIdAndStatus(
            Long fromAccountId, Long toAccountId, TransferStatus status);

    long countByFromAccountIdAndCreatedAtAfter(Long fromAccountId, Instant since);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transfer t where t.id = :id")
    Optional<Transfer> findByIdForUpdate(@Param("id") Long id);

    List<Transfer> findByFromAccountUserEmailOrderByCreatedAtDesc(String email);

    List<Transfer> findByStatusOrderByCreatedAtAsc(TransferStatus status);

    List<Transfer> findByStatusAndFromAccountUserIdIn(TransferStatus status, Collection<Long> userIds);

    @Query("select t.id from Transfer t where t.status = :status and t.releaseAt <= :now")
    List<Long> findDueHoldIds(@Param("status") TransferStatus status, @Param("now") Instant now);

    @Query("select t.id from Transfer t where t.status in (:statuses) and t.deadlineAt <= :now")
    List<Long> findExpiredIds(@Param("statuses") Collection<TransferStatus> statuses,
                              @Param("now") Instant now);
}