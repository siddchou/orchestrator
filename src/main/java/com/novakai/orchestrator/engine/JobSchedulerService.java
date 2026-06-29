package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.entity.JobSchedule;
import com.novakai.orchestrator.domain.enums.TriggerType;
import com.novakai.orchestrator.engine.exception.InvalidCronExpressionException;
import com.novakai.orchestrator.engine.exception.JobAlreadyRunningException;
import com.novakai.orchestrator.repository.JobScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnWebApplication
public class JobSchedulerService {

    private final TaskScheduler taskScheduler;
    private final JobScheduleRepository scheduleRepo;
    private final JobLaunchService launchService;

    private final Map<Long, ScheduledFuture<?>> scheduledFutures = new ConcurrentHashMap<>();
    private final Map<Long, Long> scheduleJobIdMap = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void initSchedules() {
        List<JobSchedule> enabled = scheduleRepo.findByEnabled("Y");
        log.info("Loading {} enabled schedules from DB", enabled.size());
        enabled.forEach(this::register);
    }

    public void register(JobSchedule schedule) {
        cancelIfRunning(schedule.getScheduleId());
        Long jobId = schedule.getJobDefinition().getJobId();
        try {
            CronTrigger trigger = new CronTrigger(schedule.getCronExpression());
            ScheduledFuture<?> future = taskScheduler.schedule(
                () -> fireSafe(schedule.getScheduleId(), jobId),
                trigger
            );
            scheduledFutures.put(schedule.getScheduleId(), future);
            scheduleJobIdMap.put(schedule.getScheduleId(), jobId);
            log.info("Registered schedule {} for job {} ({})",
                schedule.getScheduleId(), jobId, schedule.getCronExpression());
        } catch (IllegalArgumentException ex) {
            log.error("Invalid cron expression for schedule {}: {}",
                schedule.getScheduleId(), schedule.getCronExpression());
            throw new InvalidCronExpressionException(schedule.getCronExpression());
        }
    }

    public void cancel(Long scheduleId) {
        cancelIfRunning(scheduleId);
        scheduleJobIdMap.remove(scheduleId);
        log.info("Cancelled schedule {}", scheduleId);
    }

    public void updateSchedule(JobSchedule schedule) {
        register(schedule);
    }

    void fireSafe(Long scheduleId, Long jobId) {
        try {
            log.info("Schedule {} firing for job {}", scheduleId, jobId);
            launchService.launch(jobId, TriggerType.SCHEDULED, "scheduler");
        } catch (JobAlreadyRunningException ex) {
            log.warn("Schedule {} skipped — job {} is already running", scheduleId, jobId);
        } catch (Exception ex) {
            log.error("Schedule {} failed to launch job {}: {}", scheduleId, jobId, ex.getMessage(), ex);
        }
    }

    private void cancelIfRunning(Long scheduleId) {
        ScheduledFuture<?> existing = scheduledFutures.remove(scheduleId);
        if (existing != null && !existing.isCancelled()) {
            existing.cancel(false);
        }
    }
}
