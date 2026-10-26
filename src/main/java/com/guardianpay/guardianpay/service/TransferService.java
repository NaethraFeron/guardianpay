package com.guardianpay.guardianpay.service;

import com.guardianpay.guardianpay.dto.TransferRequest;
import com.guardianpay.guardianpay.dto.TransferResponse;
import com.guardianpay.guardianpay.dto.TransferView;
import com.guardianpay.guardianpay.model.Account;
import com.guardianpay.guardianpay.model.AccountStatus;
import com.guardianpay.guardianpay.model.Guardian;
import com.guardianpay.guardianpay.model.Transfer;
import com.guardianpay.guardianpay.model.TransferStatus;
import com.guardianpay.guardianpay.repository.AccountRepository;
import com.guardianpay.guardianpay.repository.GuardianRepository;
import com.guardianpay.guardianpay.repository.TransferRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final GuardianRepository guardianRepository;
    private final RiskService riskService;
    private final AuditService auditService;
    private final EntityManager entityManager;

    @Value("${guardianpay.risk.approval-score}") private int approvalScore;
    @Value("${guardianpay.risk.review-score}") private int reviewScore;
    @Value("${guardianpay.transfer.hold-minutes}") private long holdMinutes;
    @Value("${guardianpay.transfer.guardian-deadline-minutes}") private long guardianDeadlineMinutes;
    @Value("${guardianpay.transfer.review-deadline-minutes}") private long reviewDeadlineMinutes;

    // ---------- Create a transfer ----------

    @Transactional
    public TransferResponse transfer(String senderEmail, TransferRequest req) {
        Long senderId = accountRepository.findByUserEmail(senderEmail)
                .orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "Your account was not found"))
                .getId();
        Long receiverId = req.toAccountId();

        if (senderId.equals(receiverId)) {
            throw fail(HttpStatus.BAD_REQUEST, "You cannot transfer to your own account");
        }

        // Lock both accounts in the same order every time so transfers can't deadlock
        Account first = lockAccount(Math.min(senderId, receiverId));
        Account second = lockAccount(Math.max(senderId, receiverId));
        Account sender = first.getId().equals(senderId) ? first : second;
        Account receiver = (sender == first) ? second : first;

        if (sender.getStatus() != AccountStatus.ACTIVE
                || receiver.getStatus() != AccountStatus.ACTIVE) {
            throw fail(HttpStatus.BAD_REQUEST, "Both accounts must be active");
        }
        if (sender.getBalance().compareTo(req.amount()) < 0) {
            throw fail(HttpStatus.BAD_REQUEST, "Insufficient balance");
        }

        // Risk check runs BEFORE any money moves, using the balance before this transfer
        RiskService.RiskResult risk = riskService.evaluate(sender, receiver, req.amount());
        List<String> reasons = new ArrayList<>(risk.reasons());
        Instant now = Instant.now();

        Transfer transfer = new Transfer();
        transfer.setFromAccount(sender);
        transfer.setToAccount(receiver);
        transfer.setAmount(req.amount());
        transfer.setRiskScore(risk.score());

        // The money always leaves the sender now: it is either delivered or reserved
        sender.setBalance(sender.getBalance().subtract(req.amount()));

        String message;
        if (risk.score() >= reviewScore) {
            transfer.setStatus(TransferStatus.UNDER_REVIEW);
            transfer.setDeadlineAt(now.plus(reviewDeadlineMinutes, ChronoUnit.MINUTES));
            message = "High risk: sent for admin review. Your money is reserved and is returned if it is not approved in time.";
        } else if (risk.score() >= approvalScore) {
            Guardian guardian = guardianRepository
                    .findByProtectedUserId(sender.getUser().getId()).orElse(null);
            if (guardian == null) {
                transfer.setStatus(TransferStatus.HELD);
                transfer.setReleaseAt(now.plus(holdMinutes, ChronoUnit.MINUTES));
                message = "Held for " + holdMinutes + " minutes. You can cancel before it is released.";
            } else if (guardian.getGuardianUser().getId().equals(receiver.getUser().getId())) {
                transfer.setStatus(TransferStatus.UNDER_REVIEW);
                transfer.setDeadlineAt(now.plus(reviewDeadlineMinutes, ChronoUnit.MINUTES));
                reasons.add("Recipient is your guardian, so an admin must review");
                message = "Sent for admin review because the recipient is your guardian.";
            } else {
                transfer.setStatus(TransferStatus.PENDING_GUARDIAN);
                transfer.setDeadlineAt(now.plus(guardianDeadlineMinutes, ChronoUnit.MINUTES));
                message = "Waiting for your guardian's approval. It is cancelled and refunded if not approved in time.";
            }
        } else {
            receiver.setBalance(receiver.getBalance().add(req.amount()));
            transfer.setStatus(TransferStatus.COMPLETED);
            message = "Completed";
        }

        transfer.setReasons(String.join("; ", reasons));
        transferRepository.save(transfer);
        auditService.log(senderEmail, "TRANSFER_" + transfer.getStatus(),
                "transfer " + transfer.getId() + ", amount " + req.amount()
                        + ", score " + risk.score());

        return new TransferResponse(transfer.getId(), transfer.getStatus(), transfer.getAmount(),
                receiver.getId(), sender.getBalance(), risk.score(), reasons, message);
    }

    // ---------- Lists ----------

    @Transactional(readOnly = true)
    public List<TransferView> myTransfers(String email) {
        return transferRepository.findByFromAccountUserEmailOrderByCreatedAtDesc(email)
                .stream().map(TransferView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TransferView> reviewQueue() {
        return transferRepository.findByStatusOrderByCreatedAtAsc(TransferStatus.UNDER_REVIEW)
                .stream().map(TransferView::from).toList();
    }

    // ---------- Actions ----------

    @Transactional
    public TransferView cancelBySender(String email, Long transferId) {
        Transfer transfer = lockTransfer(transferId);
        if (!transfer.getFromAccount().getUser().getEmail().equals(email)) {
            throw fail(HttpStatus.NOT_FOUND, "Transfer not found");
        }
        if (transfer.getStatus() != TransferStatus.HELD
                && transfer.getStatus() != TransferStatus.PENDING_GUARDIAN) {
            throw fail(HttpStatus.CONFLICT, "This transfer can no longer be cancelled");
        }
        refund(transfer, TransferStatus.CANCELLED);
        auditService.log(email, "TRANSFER_CANCELLED", "transfer " + transferId);
        return TransferView.from(transfer);
    }

    @Transactional
    public TransferView guardianDecision(String guardianEmail, Long transferId, boolean approve) {
        Transfer transfer = lockTransfer(transferId);
        if (transfer.getStatus() != TransferStatus.PENDING_GUARDIAN) {
            throw fail(HttpStatus.CONFLICT, "This transfer is not waiting for guardian approval");
        }
        Guardian guardian = guardianRepository
                .findByProtectedUserId(transfer.getFromAccount().getUser().getId())
                .orElseThrow(() -> fail(HttpStatus.FORBIDDEN, "You are not this user's guardian"));
        if (!guardian.getGuardianUser().getEmail().equals(guardianEmail)) {
            throw fail(HttpStatus.FORBIDDEN, "You are not this user's guardian");
        }
        if (transfer.getToAccount().getUser().getEmail().equals(guardianEmail)) {
            throw fail(HttpStatus.FORBIDDEN, "A guardian cannot approve a transfer to themselves");
        }
        if (approve) {
            deliver(transfer);
        } else {
            refund(transfer, TransferStatus.REJECTED);
        }
        auditService.log(guardianEmail, approve ? "GUARDIAN_APPROVED" : "GUARDIAN_REJECTED",
                "transfer " + transferId);
        return TransferView.from(transfer);
    }

    @Transactional
    public TransferView adminDecision(String adminEmail, Long transferId, boolean approve) {
        Transfer transfer = lockTransfer(transferId);
        if (transfer.getStatus() != TransferStatus.UNDER_REVIEW) {
            throw fail(HttpStatus.CONFLICT, "This transfer is not under review");
        }
        if (transfer.getFromAccount().getUser().getEmail().equals(adminEmail)
                || transfer.getToAccount().getUser().getEmail().equals(adminEmail)) {
            throw fail(HttpStatus.FORBIDDEN, "You cannot review a transfer you are part of");
        }
        if (approve) {
            deliver(transfer);
        } else {
            refund(transfer, TransferStatus.REJECTED);
        }
        auditService.log(adminEmail, approve ? "ADMIN_APPROVED" : "ADMIN_REJECTED",
                "transfer " + transferId);
        return TransferView.from(transfer);
    }

    // ---------- Called by the timer ----------

    @Transactional
    public void releaseHold(Long transferId) {
        Transfer transfer = transferRepository.findByIdForUpdate(transferId).orElse(null);
        if (transfer == null || transfer.getStatus() != TransferStatus.HELD
                || transfer.getReleaseAt() == null
                || transfer.getReleaseAt().isAfter(Instant.now())) {
            return;
        }
        deliver(transfer);
        auditService.log("system", "HOLD_RELEASED", "transfer " + transferId);
    }

    @Transactional
    public void expire(Long transferId) {
        Transfer transfer = transferRepository.findByIdForUpdate(transferId).orElse(null);
        if (transfer == null
                || (transfer.getStatus() != TransferStatus.PENDING_GUARDIAN
                    && transfer.getStatus() != TransferStatus.UNDER_REVIEW)
                || transfer.getDeadlineAt() == null
                || transfer.getDeadlineAt().isAfter(Instant.now())) {
            return;
        }
        refund(transfer, TransferStatus.CANCELLED);
        auditService.log("system", "DEADLINE_EXPIRED",
                "transfer " + transferId + " cancelled and refunded");
    }

    // ---------- Helpers ----------

    // The money already left the sender when the transfer was created, so only credit the receiver
    private void deliver(Transfer transfer) {
        Account receiver = lockAccount(transfer.getToAccount().getId());
        receiver.setBalance(receiver.getBalance().add(transfer.getAmount()));
        transfer.setStatus(TransferStatus.COMPLETED);
    }

    private void refund(Transfer transfer, TransferStatus newStatus) {
        Account sender = lockAccount(transfer.getFromAccount().getId());
        sender.setBalance(sender.getBalance().add(transfer.getAmount()));
        transfer.setStatus(newStatus);
    }

    private Transfer lockTransfer(Long id) {
        return transferRepository.findByIdForUpdate(id)
                .orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "Transfer not found"));
    }

    private Account lockAccount(Long id) {
        Account account = accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "Account not found: " + id));
        entityManager.refresh(account); // reload the balance now that we hold the lock
        return account;
    }

    private ResponseStatusException fail(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}