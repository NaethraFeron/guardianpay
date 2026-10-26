package com.guardianpay.guardianpay.service;

import com.guardianpay.guardianpay.dto.TransferView;
import com.guardianpay.guardianpay.model.Guardian;
import com.guardianpay.guardianpay.model.TransferStatus;
import com.guardianpay.guardianpay.model.User;
import com.guardianpay.guardianpay.repository.GuardianRepository;
import com.guardianpay.guardianpay.repository.TransferRepository;
import com.guardianpay.guardianpay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GuardianService {

    private final UserRepository userRepository;
    private final GuardianRepository guardianRepository;
    private final TransferRepository transferRepository;
    private final AuditService auditService;

    @Transactional
    public void setGuardian(String email, String guardianEmail) {
        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (me.getEmail().equalsIgnoreCase(guardianEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot be your own guardian");
        }
        if (guardianRepository.findByProtectedUserId(me.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a guardian");
        }
        User guardianUser = userRepository.findByEmail(guardianEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No user with that email"));

        Guardian guardian = new Guardian();
        guardian.setProtectedUser(me);
        guardian.setGuardianUser(guardianUser);
        guardianRepository.save(guardian);
        auditService.log(email, "GUARDIAN_SET", "guardian " + guardianEmail);
    }

    @Transactional(readOnly = true)
    public List<TransferView> pendingFor(String guardianEmail) {
        List<Long> protectedUserIds = guardianRepository.findByGuardianUserEmail(guardianEmail)
                .stream().map(g -> g.getProtectedUser().getId()).toList();
        if (protectedUserIds.isEmpty()) {
            return List.of();
        }
        return transferRepository
                .findByStatusAndFromAccountUserIdIn(TransferStatus.PENDING_GUARDIAN, protectedUserIds)
                .stream().map(TransferView::from).toList();
    }
}