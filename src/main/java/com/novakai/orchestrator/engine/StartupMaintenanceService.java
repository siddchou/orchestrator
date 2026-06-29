package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.repository.JobRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StartupMaintenanceService {

    private final JobRunRepository runRepo;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupStaleRuns() {
        List<JobRun> stale = runRepo.findAllByStatus(RunStatus.RUNNING);
        if (stale.isEmpty()) {
            return;
        }
        stale.forEach(run -> {
            run.setStatus(RunStatus.FAILED);
            run.setEndedAt(LocalDateTime.now());
        });
        runRepo.saveAll(stale);
        log.warn("Marked {} stale RUNNING jobs as FAILED after restart", stale.size());
    }
}
