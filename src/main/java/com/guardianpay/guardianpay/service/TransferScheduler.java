package com.guardianpay.guardianpay.service;

import com.guardianpay.guardianpay.model.TransferStatus;
import com.guardianpay.guardianpay.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferScheduler {

    private final TransferRepository transferRepository;
    private final TransferService transferService;

    @Scheduled(fixedDelay = 10_000)
    public void processDueTransfers() {
        Instant now = Instant.now();

        for (Long id : transferRepository.findDueHoldIds(TransferStatus.HELD, now)) {
            try {
                transferService.releaseHold(id);
            } catch (Exception e) {
                log.error("Could not release hold for transfer {}", id, e);
            }
        }

        List<TransferStatus> waiting = List.of(TransferStatus.PENDING_GUARDIAN, TransferStatus.UNDER_REVIEW);
        for (Long id : transferRepository.findExpiredIds(waiting, now)) {
            try {
                transferService.expire(id);
            } catch (Exception e) {
                log.error("Could not expire transfer {}", id, e);
            }
        }
    }
}