package com.guardianpay.guardianpay.service;

import com.guardianpay.guardianpay.model.Account;
import com.guardianpay.guardianpay.model.TransferStatus;
import com.guardianpay.guardianpay.repository.TransferRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class RiskService {

    public record RiskResult(int score, List<String> reasons) {}

    private final TransferRepository transferRepository;
    private final BigDecimal largeAmount;
    private final int mostOfBalancePercent;
    private final int rapidCount;
    private final long rapidWindowMinutes;

    public RiskService(TransferRepository transferRepository,
                       @Value("${guardianpay.risk.large-amount}") BigDecimal largeAmount,
                       @Value("${guardianpay.risk.most-of-balance-percent}") int mostOfBalancePercent,
                       @Value("${guardianpay.risk.rapid-count}") int rapidCount,
                       @Value("${guardianpay.risk.rapid-window-minutes}") long rapidWindowMinutes) {
        this.transferRepository = transferRepository;
        this.largeAmount = largeAmount;
        this.mostOfBalancePercent = mostOfBalancePercent;
        this.rapidCount = rapidCount;
        this.rapidWindowMinutes = rapidWindowMinutes;
    }

    public RiskResult evaluate(Account sender, Account receiver, BigDecimal amount) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        // 1. New payee: sender has never completed a transfer to this receiver
        boolean knownPayee = transferRepository.existsByFromAccountIdAndToAccountIdAndStatus(
                sender.getId(), receiver.getId(), TransferStatus.COMPLETED);
        if (!knownPayee) {
            score += 1;
            reasons.add("New payee");
        }

        // 2. Large amount
        if (amount.compareTo(largeAmount) > 0) {
            score += 2;
            reasons.add("Amount exceeds INR " + largeAmount.toPlainString());
        }

        // 3. Most of the balance (balance before this transfer)
        BigDecimal limit = sender.getBalance()
                .multiply(BigDecimal.valueOf(mostOfBalancePercent))
                .divide(BigDecimal.valueOf(100));
        if (amount.compareTo(limit) > 0) {
            score += 1;
            reasons.add("Amount is more than " + mostOfBalancePercent + "% of your balance");
        }

        // 4. Rapid transfers: earlier attempts (any status) in the time window
        Instant since = Instant.now().minus(rapidWindowMinutes, ChronoUnit.MINUTES);
        long recent = transferRepository.countByFromAccountIdAndCreatedAtAfter(sender.getId(), since);
        if (recent >= rapidCount) {
            score += 2;
            reasons.add(recent + " transfer attempts in the last " + rapidWindowMinutes + " minutes");
        }

        return new RiskResult(score, reasons);
    }
}