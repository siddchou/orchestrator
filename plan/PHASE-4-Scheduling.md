# Phase 4 — Dynamic Cron Scheduling Engine

> **Goal:** Allow jobs to be triggered automatically on a cron schedule. Schedules must
> be manageable at runtime (create, update, enable, disable, delete) without restarting
> the application. Uses Spring's `TaskScheduler` — no Quartz dependency required.

---

## 4.1 Why Not `@Scheduled`?

`@Scheduled` annotations are fixed at compile time. For a configurable platform where
schedules are stored in the DB and changed via the UI, you need `TaskScheduler` with
`ScheduledFuture` handles so individual schedules can be cancelled and re-registered
independently.

---

## 4.2 Scheduler Service

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class JobSchedulerService {

    private final TaskScheduler taskScheduler;
    private final JobScheduleRepository scheduleRepo;
    private final JobLaunchService launchService;

    // Active scheduled futures — keyed by scheduleId
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> scheduledFutures
        = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------
    // Startup — register all enabled schedules from DB
    // ---------------------------------------------------------------

    @PostConstruct
    public void initSchedules() {
        List<JobSchedule> enabled = scheduleRepo.findByEnabled("Y");
        log.info("Loading {} enabled schedules from DB", enabled.size());
        enabled.forEach(this::register);
    }

    // ---------------------------------------------------------------
    // Public API — called by the REST layer when schedules change
    // ---------------------------------------------------------------

    public void register(JobSchedule schedule) {
        cancelIfRunning(schedule.getScheduleId());
        try {
            CronTrigger trigger = new CronTrigger(schedule.getCronExpression());
            ScheduledFuture<?> future = taskScheduler.schedule(
                () -> fireSafe(schedule),
                trigger
            );
            scheduledFutures.put(schedule.getScheduleId(), future);
            log.info("Registered schedule {} for job {} ({})",
                schedule.getScheduleId(),
                schedule.getJobDefinition().getJobId(),
                schedule.getCronExpression());
        } catch (IllegalArgumentException ex) {
            log.error("Invalid cron expression for schedule {}: {}",
                schedule.getScheduleId(), schedule.getCronExpression());
            throw new InvalidCronExpressionException(schedule.getCronExpression());
        }
    }

    public void cancel(Long scheduleId) {
        cancelIfRunning(scheduleId);
        log.info("Cancelled schedule {}", scheduleId);
    }

    public void updateSchedule(JobSchedule schedule) {
        // Cancel existing and re-register with new expression
        register(schedule);
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    private void fireSafe(JobSchedule schedule) {
        Long jobId = schedule.getJobDefinition().getJobId();
        try {
            log.info("Schedule {} firing for job {}", schedule.getScheduleId(), jobId);
            launchService.launch(jobId, TriggerType.SCHEDULED, "scheduler");
        } catch (JobAlreadyRunningException ex) {
            log.warn("Schedule {} skipped — job {} is already running",
                schedule.getScheduleId(), jobId);
            // Record a SKIPPED event in audit log if desired
        } catch (Exception ex) {
            log.error("Schedule {} failed to launch job {}: {}",
                schedule.getScheduleId(), jobId, ex.getMessage(), ex);
        }
    }

    private void cancelIfRunning(Long scheduleId) {
        ScheduledFuture<?> existing = scheduledFutures.remove(scheduleId);
        if (existing != null && !existing.isCancelled()) {
            existing.cancel(false);   // false = don't interrupt if currently executing
        }
    }
}
```

---

## 4.3 TaskScheduler Bean

```java
@Configuration
public class SchedulerConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);              // 5 concurrent cron threads
        scheduler.setThreadNamePrefix("cron-");
        scheduler.setErrorHandler(t ->
            LoggerFactory.getLogger(SchedulerConfig.class)
                .error("Uncaught error in scheduled task", t));
        scheduler.initialize();
        return scheduler;
    }
}
```

---

## 4.4 Integration with Schedule REST Endpoints

The `JobDefinitionService` (Phase 3) must call `JobSchedulerService` whenever a schedule
changes through the API. Integrate at the service layer, not the controller.

```java
// In JobDefinitionService

public JobScheduleResponse createSchedule(Long jobId, JobScheduleRequest request) {
    validateCronExpression(request.cronExpression());

    JobSchedule schedule = JobSchedule.builder()
        .jobDefinition(jobRepo.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId)))
        .cronExpression(request.cronExpression())
        .enabled("Y")
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
    schedule = scheduleRepo.save(schedule);

    schedulerService.register(schedule);     // ← register immediately

    return mapper.toResponse(schedule);
}

public JobScheduleResponse updateSchedule(Long jobId, JobScheduleRequest request) {
    JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
        .orElseThrow(() -> new NotFoundException("No schedule for job " + jobId));

    validateCronExpression(request.cronExpression());
    schedule.setCronExpression(request.cronExpression());
    schedule.setUpdatedAt(LocalDateTime.now());
    schedule = scheduleRepo.save(schedule);

    schedulerService.updateSchedule(schedule);   // ← cancel old, register new

    return mapper.toResponse(schedule);
}

public void deleteSchedule(Long jobId) {
    JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
        .orElseThrow(() -> new NotFoundException("No schedule for job " + jobId));

    schedulerService.cancel(schedule.getScheduleId());   // ← cancel first
    scheduleRepo.delete(schedule);
}

public JobScheduleResponse toggleSchedule(Long jobId, boolean enable) {
    JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
        .orElseThrow(() -> new NotFoundException("No schedule for job " + jobId));

    schedule.setEnabled(enable ? "Y" : "N");
    schedule.setUpdatedAt(LocalDateTime.now());
    schedule = scheduleRepo.save(schedule);

    if (enable) {
        schedulerService.register(schedule);
    } else {
        schedulerService.cancel(schedule.getScheduleId());
    }

    return mapper.toResponse(schedule);
}

private void validateCronExpression(String expression) {
    try {
        new CronTrigger(expression);
    } catch (IllegalArgumentException ex) {
        throw new InvalidCronExpressionException(expression);
    }
}
```

---

## 4.5 Cron Expression Validation Endpoint

Expose a helper so the Angular UI can validate cron expressions before saving.

```java
@GetMapping("/api/system/cron-validate")
public ApiResponse<Map<String, String>> validateCron(
        @RequestParam String expression) {
    try {
        CronTrigger trigger = new CronTrigger(expression);
        // Compute next 3 fire times for user-friendly display
        CronSequenceGenerator gen = new CronSequenceGenerator(expression);
        Date next1 = gen.next(new Date());
        Date next2 = gen.next(next1);
        Date next3 = gen.next(next2);
        return ApiResponse.success(Map.of(
            "valid", "true",
            "next1", next1.toString(),
            "next2", next2.toString(),
            "next3", next3.toString()
        ));
    } catch (IllegalArgumentException ex) {
        return ApiResponse.success(Map.of(
            "valid", "false",
            "error", ex.getMessage()
        ));
    }
}
```

---

## 4.6 Overlap Prevention Detail

`JobLaunchService.launch()` already checks for concurrent runs (Phase 2):

```java
if (runRepo.existsByJobDefinition_JobIdAndStatus(jobId, RunStatus.RUNNING)) {
    throw new JobAlreadyRunningException(jobId);
}
```

`JobSchedulerService.fireSafe()` catches `JobAlreadyRunningException` and logs a warning
rather than propagating it, so the scheduler thread is not killed by a skipped fire.

---

## 4.7 Application Restart Recovery

On restart, `@PostConstruct` in `JobSchedulerService` reloads all enabled schedules
from the DB. Jobs that were `RUNNING` at shutdown are detected on startup and their
status should be reset:

```java
// In a separate StartupMaintenanceService

@PostConstruct
public void cleanupStaleRuns() {
    // Any run still in RUNNING state at startup was interrupted by a shutdown
    List<JobRun> stale = runRepo.findByStatus(RunStatus.RUNNING);
    stale.forEach(run -> {
        run.setStatus(RunStatus.FAILED);
        run.setEndedAt(LocalDateTime.now());
    });
    if (!stale.isEmpty()) {
        runRepo.saveAll(stale);
        log.warn("Marked {} stale RUNNING jobs as FAILED after restart", stale.size());
    }
}
```

---

## 4.8 Cron Expression Reference

Provide this in the UI help text:

| Expression | Meaning |
|------------|---------|
| `0 0 2 * * *` | Every day at 2:00 AM |
| `0 0/30 9-17 * * MON-FRI` | Every 30 min, 9am–5pm, weekdays |
| `0 0 8 1 * *` | 8:00 AM on the 1st of every month |
| `0 0 6 * * SAT` | Every Saturday at 6:00 AM |
| `0 */15 * * * *` | Every 15 minutes |

> Spring's `CronTrigger` uses **6-field** cron with seconds:
> `[seconds] [minutes] [hours] [day-of-month] [month] [day-of-week]`

---

## Phase 4 Acceptance Criteria

- [ ] On startup, all enabled schedules are loaded from DB and registered
- [ ] Creating a schedule via API fires immediately registers it with `TaskScheduler`
- [ ] Updating a cron expression cancels the old schedule and registers the new one
- [ ] Disabling a schedule cancels its `ScheduledFuture` — no more fires
- [ ] Enabling a schedule re-registers it
- [ ] Deleting a schedule cancels it before removing from DB
- [ ] Overlapping fires are skipped with a warning — the scheduler thread is not killed
- [ ] Stale `RUNNING` runs are marked `FAILED` on application startup
- [ ] `GET /api/system/cron-validate` returns next 3 fire times for a valid expression
- [ ] Integration test: schedule a job for `*/5 * * * * *` and verify it fires 3 times

---

**Previous:** [Phase 3 — REST API](./PHASE-3-API.md)  
**Next:** [Phase 5 — Angular UI](./PHASE-5-UI.md)
