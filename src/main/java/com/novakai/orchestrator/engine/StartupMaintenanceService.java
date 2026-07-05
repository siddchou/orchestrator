package com.novakai.orchestrator.engine;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.repository.JobRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class StartupMaintenanceService {

    private final JobRunRepository runRepo;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupStaleRuns() {
        int totalCount = 0;
        int batchSize = 1000;
        Pageable pageable = PageRequest.of(0, batchSize);

        while (true) {
            Page<JobRun> page = runRepo.findByStatus(RunStatus.RUNNING, pageable);
            if (page.isEmpty()) {
                break;
            }

            totalCount += page.getContent().size();

            for (JobRun run : page.getContent()) {
                run.setStatus(RunStatus.FAILED);
                run.setEndedAt(LocalDateTime.now());
            }
            runRepo.saveAll(page.getContent());

            if (!page.hasNext()) {
                break;
            }
            pageable = page.nextPageable();
        }

        log.warn("Marked {} stale RUNNING jobs as FAILED after restart", totalCount);
    }
}
