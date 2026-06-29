# Phase 4b — Scheduler Integration: Service Wiring & Cron Validation

> **Goal:** Wire `JobSchedulerService` into `JobDefinitionService` so every schedule
> change through the REST API is immediately reflected in the live scheduler.
> Also add the cron validation endpoint to `SystemController`.

> **Depends on:** Phase 4a (`JobSchedulerService`), Phase 3b (`JobDefinitionService`),
> Phase 3c (`SystemController`)  
> **Produces:** Updated `JobDefinitionService` (schedule methods), updated `SystemController`

---

## 4b.1 What Changes in `JobDefinitionService`

Phase 3b left `// schedulerService.xxx ← wired in Phase 4b` comments in the four
schedule methods. This sub-phase fills those in.

Inject `JobSchedulerService` into `JobDefinitionService`:

```java
// Add to the field list in JobDefinitionService

@RequiredArgsConstructor  // Lombok — adds this to the constructor automatically
public class JobDefinitionService {
    // ... existing fields ...
    private final JobSchedulerService schedulerService;   // ← ADD THIS
    // ...
}
```

---

## 4b.2 Updated Schedule Methods

Replace the four schedule methods from Phase 3b with these wired versions.
Only the scheduler calls are new — the DB logic is identical.

### `createSchedule`

```java
public JobScheduleResponse createSchedule(Long jobId, JobScheduleRequest request) {
    findJob(jobId);  // validate job exists

    if (scheduleRepo.findByJobDefinition_JobId(jobId).isPresent()) {
        throw new IllegalStateException(
            "Schedule already exists for job " + jobId + ". Use PUT to update.");
    }

    // Validate cron before saving to DB
    validateCronExpression(request.cronExpression());

    JobDefinition job = findJob(jobId);
    JobSchedule schedule = JobSchedule.builder()
        .jobDefinition(job)
        .cronExpression(request.cronExpression())
        .enabled("Y")
        .createdAt(LocalDateTime.now())
        .updatedAt(LocalDateTime.now())
        .build();
    schedule = scheduleRepo.save(schedule);

    schedulerService.register(schedule);    // ← WIRED: register with live scheduler

    log.info("Created and registered schedule {} for job {}", schedule.getScheduleId(), jobId);
    return mapper.toResponse(schedule);
}
```

### `updateSchedule`

```java
public JobScheduleResponse updateSchedule(Long jobId, JobScheduleRequest request) {
    JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
        .orElseThrow(() -> new IllegalStateException("No schedule for job " + jobId));

    validateCronExpression(request.cronExpression());

    schedule.setCronExpression(request.cronExpression());
    schedule.setUpdatedAt(LocalDateTime.now());
    schedule = scheduleRepo.save(schedule);

    schedulerService.update(schedule);      // ← WIRED: cancel old + register new

    log.info("Updated schedule {} for job {} to '{}'",
        schedule.getScheduleId(), jobId, request.cronExpression());
    return mapper.toResponse(schedule);
}
```

### `deleteSchedule`

```java
public void deleteSchedule(Long jobId) {
    JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
        .orElseThrow(() -> new IllegalStateException("No schedule for job " + jobId));

    schedulerService.cancel(schedule.getScheduleId());   // ← WIRED: cancel before delete
    scheduleRepo.delete(schedule);

    log.info("Deleted schedule {} for job {}", schedule.getScheduleId(), jobId);
}
```

### `toggleSchedule`

```java
public JobScheduleResponse toggleSchedule(Long jobId, boolean enable) {
    JobSchedule schedule = scheduleRepo.findByJobDefinition_JobId(jobId)
        .orElseThrow(() -> new IllegalStateException("No schedule for job " + jobId));

    schedule.setEnabled(enable ? "Y" : "N");
    schedule.setUpdatedAt(LocalDateTime.now());
    schedule = scheduleRepo.save(schedule);

    if (enable) {
        schedulerService.register(schedule);              // ← WIRED: re-register
        log.info("Enabled schedule {} for job {}", schedule.getScheduleId(), jobId);
    } else {
        schedulerService.cancel(schedule.getScheduleId()); // ← WIRED: cancel
        log.info("Disabled schedule {} for job {}", schedule.getScheduleId(), jobId);
    }

    return mapper.toResponse(schedule);
}
```

---

## 4b.3 Shared Cron Validation Helper

Add this private method to `JobDefinitionService`. It throws before hitting the DB
so invalid expressions are rejected at the API boundary.

```java
// Private helper — used by createSchedule and updateSchedule

private void validateCronExpression(String expression) {
    try {
        // Spring's CronExpression (6-field) validator
        CronExpression.parse(expression);
    } catch (IllegalArgumentException ex) {
        throw new InvalidCronExpressionException(expression);
    }
}
```

> **Import:** `org.springframework.scheduling.support.CronExpression`

---

## 4b.4 `SystemController` — Cron Validation Endpoint Update

If you implemented the `/api/system/cron-validate` endpoint in Phase 3c using the
deprecated `CronSequenceGenerator`, replace it with `CronExpression`:

```java
// In SystemController — updated cron-validate endpoint

@GetMapping("/system/cron-validate")
public ApiResponse<Map<String, Object>> validateCron(
        @RequestParam String expression) {
    try {
        CronExpression parsed = CronExpression.parse(expression);

        // Compute next 3 fire times
        LocalDateTime t = LocalDateTime.now();
        List<String> nextFires = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            t = parsed.next(t);
            if (t == null) break;
            nextFires.add(t.toString());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", true);
        result.put("nextFires", nextFires);
        return ApiResponse.success(result);

    } catch (IllegalArgumentException ex) {
        return ApiResponse.success(Map.of(
            "valid", false,
            "error", ex.getMessage()
        ));
    }
}
```

---

## 4b.5 Job Delete Cascade to Scheduler

When a job is deleted (Phase 3b `deleteJob`), if it has an active schedule the
`JobSchedulerService` must also be notified. Add this to `deleteJob` in
`JobDefinitionService`:

```java
public void deleteJob(Long id) {
    JobDefinition job = findJob(id);

    // Cancel any active schedule before deleting the job
    scheduleRepo.findByJobDefinition_JobId(id).ifPresent(schedule -> {
        schedulerService.cancel(schedule.getScheduleId());
        log.info("Cancelled schedule {} due to job {} deletion",
            schedule.getScheduleId(), id);
    });

    jobRepo.delete(job);
    log.info("Deleted job {}", id);
}
```

---

## 4b.6 Overlap Prevention — How It Works End-to-End

```
Cron fires
    │
    ▼
JobSchedulerService.fireSafe()
    │
    ▼ calls
JobLaunchService.launch(jobId, SCHEDULED, "scheduler")
    │
    ▼ checks
runRepo.existsByJobDefinition_JobIdAndStatus(jobId, RunStatus.RUNNING)
    │
    ├─ FALSE → create JOB_RUN, submit to async thread pool → return
    │
    └─ TRUE  → throw JobAlreadyRunningException
                    │
                    ▼
             fireSafe() catches it → log.warn("skipped") → return
             (cron thread survives, next fire will try again)
```

No additional locking needed — the DB check in `launch()` is the gate.

---

## Phase 4b Acceptance Criteria

- [ ] `POST /api/jobs/{id}/schedule` with a valid cron expression registers with `TaskScheduler` immediately
- [ ] `POST /api/jobs/{id}/schedule` with an invalid expression returns `400` — no DB row created
- [ ] `PUT /api/jobs/{id}/schedule` with a new expression cancels the old future and registers a new one
- [ ] `DELETE /api/jobs/{id}/schedule` cancels the future and removes the DB row
- [ ] `POST /api/jobs/{id}/schedule/disable` cancels the future but keeps the DB row with `enabled=N`
- [ ] `POST /api/jobs/{id}/schedule/enable` re-registers the existing DB row's expression
- [ ] `DELETE /api/jobs/{id}` cancels any active schedule before deleting the job
- [ ] A scheduled job that is already running is skipped (not double-launched)
- [ ] `GET /api/system/cron-validate` returns correct next 3 fire times using `CronExpression.parse()`
- [ ] Full round-trip integration test: create job → add schedule → wait for auto-fire → check `JOB_RUN` row

---

**Previous:** [Phase 4a — Scheduler Core](./PHASE-4a-Scheduler-Core.md)  
**Next:** [Phase 5a — UI Setup, Models & Services](./PHASE-5a-UI-Setup-Models-Services.md)
