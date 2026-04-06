package com.paybook.settlement.service;

import com.paybook.settlement.entity.SettlementEntity;
import com.paybook.settlement.entity.SettlementStatus;
import com.paybook.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private static final int CONFIRM_DELAY_DAYS = 7;
    private static final int BATCH_SIZE = 500;

    private final SettlementRepository settlementRepository;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void autoConfirmSettlements() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(CONFIRM_DELAY_DAYS);
        int confirmed = processAutoConfirm(cutoff);
        log.info("자동 정산 확정 처리: {}건", confirmed);
    }

    @Transactional
    public void autoConfirmSettlementsManually(LocalDateTime cutoff) {
        processAutoConfirm(cutoff);
    }

    private int processAutoConfirm(LocalDateTime cutoff) {
        int totalConfirmed = 0;
        Page<SettlementEntity> page;

        do {
            page = settlementRepository.findByStatusAndCreatedAtBeforeForUpdate(
                    SettlementStatus.PENDING, cutoff, PageRequest.of(0, BATCH_SIZE));

            for (SettlementEntity settlement : page.getContent()) {
                settlement.confirm();
                totalConfirmed++;
            }
        } while (page.hasNext());

        return totalConfirmed;
    }
}
