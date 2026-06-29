# Phase 4a — Scheduler Core: `JobSchedulerService` & `TaskScheduler` Bean

> **Goal:** Build the dynamic scheduling engine. Schedules are loaded from the DB at
> startup and each is registered with Spring's `TaskScheduler`. Individual schedules
> can be cancelled and re-registered at runtime without an application restart.

> **Depends on:** Phase 1 (`JobSchedule` entity, `JobScheduleRepository`),
> Phase 2 (`JobLaunchService`, `TriggerType`, `JobAlreadyRunningException`)  
> **Produces:** `SchedulerConfig`, `JobSchedulerService`

---

## Package Layout for This Sub-Phase

```
com.yourco.orchestrator/
├── config/
│   └── SchedulerConfig.java
└── service/
    └── JobSchedulerService.java
```

---

## 4a.1 Why Spring `TaskScheduler` Instead of `@Scheduled`

`@Scheduled` annotations are resolved once at application startup and cannot be
modified at runtime. For this platform, cron expressions are stored in Oracle and
can be changed by any admin through the UI — so the scheduler must be able to:

- Register a new schedule without restarting
- Cancel a specific schedule without affecting others
- Re-register an updated schedule with a new expression

`TaskScheduler` + `ScheduledFuture` handles all of this cleanly.

---

## 4a.2 `SchedulerConfig` Bean

```java
// com.yourco.orchestrator.config.SchedulerConfig

@Configuration
public class SchedulerConfig {

    /**
     * Dedicated thread pool for cron tasks.
     * Size 5 means up to 5 jobs can fire concurrently from scheduled triggers.
     * Individual job execution runs on the JobLaunchService thread pool (Phase 2),
     * so this pool is only held during the brief launch() call.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("cron-");
        scheduler.setErrorHandler(throwable ->
            LoggerFactory.getLogger(SchedulerConfig.class)
                .error("Uncaught error in scheduled cron task", throwable));
        scheduler.setWaitForTasksToCompleteOnShutdown(false);  // cron fires are brief
        scheduler.initialize();
        return scheduler;
    }
}
```

---

## 4a.3 `JobSchedulerService`

```java
// com.yourco.orchestrator.service.JobSchedulerService

@Service
@RequiredArgsConstructor
@Slf4j
public class JobSchedulerService {

    private final TaskScheduler taskScheduler;
    private final JobScheduleRepository scheduleRepo;
    private final JobLaunchService launchService;

    /**
     * Active ScheduledFutures keyed by scheduleId.
     * Used to cancel individual schedules without restarting the app.
     */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> activeFutures
        = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------
    // Startup — load all enabled schedules from DB
    // ---------------------------------------------------------------

    /**
     * Called once after all Spring beans are initialized.
     * Registers every enabled schedule from the DB with the TaskScheduler.
     */
    @PostConstruct
    public void initSchedules() {
        List<JobSchedule> enabled = scheduleRepo.findByEnabled("Y");
        log.info("Initializing {} enabled schedule(s) from DB", enabled.size());
        enabled.forEach(this::register);
    }

    // ---------------------------------------------------------------
    // Public API — called from JobDefinitionService (Phase 4b wires this in)
    // ---------------------------------------------------------------

    /**
     * Register (or re-register) a schedule.
     * If a future is already active for this scheduleId, it is cancelled first.
     *
     * @throws InvalidCronExpressionException if the cron expression is malformed
     */
    public void register(JobSchedule schedule) {
        cancelIfActive(schedule.getScheduleId());

        CronTrigger trigger;
        try {
            trigger = new CronTrigger(schedule.getCronExpression());
        } catch (IllegalArgumentException ex) {
            log.error("Invalid cron expression '{}' for schedule {}",
                schedule.getCronExpression(), schedule.getScheduleId());
            throw new InvalidCronExpressionException(schedule.getCronExpression());
        }

        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> fireSafe(schedule),
            trigger
        );
        activeFutures.put(schedule.getScheduleId(), future);

        log.info("Registered schedule {} → job {} | cron: {}",
            schedule.getScheduleId(),
            schedule.getJobDefinition().getJobId(),
            schedule.getCronExpression());
    }

    /**
     * Cancel a schedule. Idempotent — safe to call even if not currently registered.
     * Does NOT delete the schedule from the DB.
     */
    public void cancel(Long scheduleId) {
        cancelIfActive(scheduleId);
        log.info("Cancelled schedule {}", scheduleId);
    }

    /**
     * Convenience method: cancel the old registration and re-register.
     * Used when a cron expression is updated.
     */
    public void update(JobSchedule schedule) {
        register(schedule);   // register() cancels first internally
    }

    /**
     * Check if a schedule is currently registered and active.
     */
    public boolean isActive(Long scheduleId) {
        ScheduledFuture<?> f = activeFutures.get(scheduleId);
        return f != null && !f.isCancelled() && !f.isDone();
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    /**
     * The actual cron task body. Runs on the cron thread pool.
     * Catches ALL exceptions so cron threads are never killed by bad job launches.
     */
    private void fireSafe(JobSchedule schedule) {
        Long jobId = schedule.getJobDefinition().getJobId();
        Long scheduleId = schedule.getScheduleId();

        try {
            log.info("Schedule {} firing for job {}", scheduleId, jobId);
            launchService.launch(jobId, TriggerType.SCHEDULED, "scheduler");
        } catch (JobAlreadyRunningException ex) {
            // Normal — the previous scheduled run hasn't finished yet
            log.warn("Schedule {} skipped — job {} is already running",
                scheduleId, jobId);
        } catch (Exception ex) {
            // Log but do not rethrow — keeps this schedule alive for the next fire
            log.error("Schedule {} failed to launch job {}: {}",
                scheduleId, jobId, ex.getMessage(), ex);
        }
    }

    private void cancelIfActive(Long scheduleId) {
        ScheduledFuture<?> existing = activeFutures.remove(scheduleId);
        if (existing != null && !existing.isCancelled()) {
            // false = do not interrupt if the task is currently executing (fires are brief)
            existing.cancel(false);
        }
    }
}
```

---

## 4a.4 Startup Stale Run Cleanup

If the app crashes while a job is `RUNNING`, that run stays in `RUNNING` state forever.
Fix this with a separate `@PostConstruct` in a dedicated maintenance service.

> **Important:** This must run BEFORE `JobSchedulerService.initSchedules()`. Use
> `@DependsOn` or rely on alphabetical bean ordering — or simply place both in a single
> `StartupService` class with stale cleanup first.

```java
// com.yourco.orchestrator.service.StartupMaintenanceService

@Service
@RequiredArgsConstructor
@Slf4j
public class StartupMaintenanceService {

    private final JobRunRepository runRepo;

    @PostConstruct
    public void cleanupStaleRuns() {
        List<JobRun> stale = runRepo.findByStatusIn(
            List.of(RunStatus.RUNNING, RunStatus.PENDING));

        if (stale.isEmpty()) return;

        stale.forEach(run -> {
            run.setStatus(RunStatus.FAILED);
            run.setEndedAt(LocalDateTime.now());
        });
        runRepo.saveAll(stale);
        log.warn("Startup cleanup: marked {} stale run(s) as FAILED", stale.size());
    }
}
```

Add to `JobRunRepository`:

```java
List<JobRun> findByStatusIn(List<RunStatus> statuses);
```

---

## 4a.5 Cron Expression Format Reference

Spring's `CronTrigger` uses a **6-field** format (seconds first):

```
[seconds] [minutes] [hours] [day-of-month] [month] [day-of-week]
```

| Expression | Meaning |
|------------|---------|
| `0 0 2 * * *` | Every day at 2:00:00 AM |
| `0 30 8 * * MON-FRI` | Weekdays at 08:30 AM |
| `0 0/30 9-17 * * MON-FRI` | Every 30 min during business hours |
| `0 0 8 1 * *` | 08:00 on the 1st of every month |
| `0 */15 * * * *` | Every 15 minutes |
| `0 0 0 * * SUN` | Every Sunday at midnight |

> This differs from standard Unix cron (5-field, no seconds).
> The UI cron builder must generate 6-field expressions.

---

## Phase 4a Acceptance Criteria

- [ ] Application starts and `@PostConstruct` logs how many schedules were loaded
- [ ] A valid cron expression registers without errors
- [ ] An invalid cron expression throws `InvalidCronExpressionException`
- [ ] `register()` on an already-registered schedule cancels the old future first
- [ ] `cancel()` on a non-existent schedule ID does not throw
- [ ] `isActive()` returns `true` for a registered schedule and `false` after cancel
- [ ] `StartupMaintenanceService` marks stale `RUNNING` and `PENDING` runs as `FAILED` on startup
- [ ] No cron thread is killed when `fireSafe()` encounters a `JobAlreadyRunningException`
- [ ] Integration test: register a `*/5 * * * * *` schedule, wait 12 seconds, verify 2 fires in `JOB_RUN`

---

**Previous:** [Phase 3c — Execution, SSE & System](./PHASE-3c-API-Execution-SSE-System.md)  
**Next:** [Phase 4b — Scheduler Integration](./PHASE-4b-Scheduler-Integration.md)
