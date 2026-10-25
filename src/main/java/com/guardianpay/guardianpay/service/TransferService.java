package com.guardianpay.guardianpay.service;

import com.guardianpay.guardianpay.dto.TransferRequest;
import com.guardianpay.guardianpay.dto.TransferResponse;
import com.guardianpay.guardianpay.model.Account;
import com.guardianpay.guardianpay.model.AccountStatus;
import com.guardianpay.guardianpay.model.Transfer;
import com.guardianpay.guardianpay.model.TransferStatus;
import com.guardianpay.guardianpay.repository.AccountRepository;
import com.guardianpay.guardianpay.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;

    @Transactional
    public TransferResponse transfer(String senderEmail, TransferRequest req) {
        Long senderId = accountRepository.findByUserEmail(senderEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Your account was not found"))
                .getId();
        Long receiverId = req.toAccountId();

        if (senderId.equals(receiverId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "You cannot transfer to your own account");
        }

        // Lock both accounts in the same order every time so transfers can't deadlock
        Account first = lockAccount(Math.min(senderId, receiverId));
        Account second = lockAccount(Math.max(senderId, receiverId));
        Account sender = first.getId().equals(senderId) ? first : second;
        Account receiver = (sender == first) ? second : first;

        if (sender.getStatus() != AccountStatus.ACTIVE
                || receiver.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Both accounts must be active");
        }
        if (sender.getBalance().compareTo(req.amount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance");
        }

        sender.setBalance(sender.getBalance().subtract(req.amount()));
        receiver.setBalance(receiver.getBalance().add(req.amount()));

        Transfer transfer = new Transfer();
        transfer.setFromAccount(sender);
        transfer.setToAccount(receiver);
        transfer.setAmount(req.amount());
        transfer.setStatus(TransferStatus.COMPLETED);
        transferRepository.save(transfer);

        return new TransferResponse(transfer.getId(), transfer.getStatus(),
                transfer.getAmount(), receiver.getId(), sender.getBalance());
    }

    private Account lockAccount(Long id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found: " + id));
    }
}