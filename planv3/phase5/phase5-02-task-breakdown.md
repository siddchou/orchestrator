<!-- FILE: phase5-02-task-breakdown.md -->
# Phase 5 — Task Breakdown

## Task 1: Add spring-boot-starter-mail dependency and SMTP properties

**Files Touched:** `pom.xml`, `src/main/resources/application.yml` (or `.properties`)

**Definition of Done:**
- `spring-boot-starter-mail` added to pom.xml dependencies
- Placeholder SMTP properties in application.yml (`spring.mail.host`, `spring.mail.port`, `spring.mail.username`, `spring.mail.password`, `spring.mail.properties.mail.smtp.auth`, `spring.mail.properties.mail.smtp.starttls.enable`)
- Application starts without error when SMTP properties are absent (properties are optional at startup)

**Test to Add:** None (dependency-only change; verified by successful build)

**Depends On:** Nothing

---

## Task 2: Create NotificationChannel SPI interface and supporting types

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/notification/spi/NotificationChannel.java` (new)
- `src/main/java/com/novakai/orchestrator/notification/spi/NotificationEvent.java` (new)
- `src/main/java/com/novakai/orchestrator/notification/spi/ChannelConfig.java` (new)
- `src/main/java/com/novakai/orchestrator/notification/spi/ChannelConfigSchema.java` (new)
- `src/main/java/com/novakai/orchestrator/notification/spi/NotificationException.java` (new)

**Definition of Done:**
- Interface compiles, follows the same structure as `StepExecutor` SPI
- `FieldDefinition` record reused from existing SPI package (import it; don't duplicate)
- `ChannelConfigSchema` mirrors `StepConfigSchema` pattern

**Test to Add:** Compilation test only — interface has no implementation logic yet.

**Depends On:** Task 1

---

## Task 3: Create NotificationChannelRegistry

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/notification/spi/NotificationChannelRegistry.java` (new)

**Definition of Done:**
- Registry mirrors `StepExecutorRegistry` exactly: ConcurrentHashMap, log-and-continue on duplicate, Optional return on miss
- Spring `@Component`, constructor-injected `List<NotificationChannel>`

**Test to Add:** `NotificationChannelRegistryTest` — test duplicate registration warning, empty lookup returns Optional.empty(), listAll returns schemas

**Depends On:** Task 2

---

## Task 4: Implement EmailNotificationChannel

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/notification/channel/EmailNotificationChannel.java` (new)

**Definition of Done:**
- Implements `NotificationChannel`, type = "EMAIL"
- Uses injected `JavaMailSender` (provided by spring-boot-starter-mail auto-config)
- Builds subject from `[STATUS] JobName completed`, body with run details
- Returns config schema with `recipients` and optional `fromAddress` fields

**Test to Add:** Unit test with mocked `JavaMailSender` — verify message content, verify exception on null recipients

**Depends On:** Task 1, Task 2

---

## Task 5: Implement SlackWebhookChannel

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/notification/channel/SlackWebhookChannel.java` (new)

**Definition of Done:**
- Implements `NotificationChannel`, type = "SLACK_WEBHOOK"
- POSTs Block Kit payload to webhook URL via RestTemplate
- Throws `NotificationException` on non-2xx response
- Config schema: `webhookUrl` (required), `channel` (optional)

**Test to Add:** Unit test with mocked `RestTemplate` — verify payload structure, verify exception on 4xx/5xx

**Depends On:** Task 2

---

## Task 6: Implement GenericWebhookChannel

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/notification/channel/GenericWebhookChannel.java` (new)

**Definition of Done:**
- Implements `NotificationChannel`, type = "GENERIC_WEBHOOK"
- Supports configurable HTTP method, headers, and JSON payload template with `{{variable}}` resolution from NotificationEvent fields
- Config schema: `webhookUrl` (required), `method`, `headers`, `payload` (all optional)

**Test to Add:** Unit test — verify template variable substitution, verify custom headers applied

**Depends On:** Task 2

---

## Task 7: Create V12 Flyway migration + JPA entities

**Files Touched:**
- `src/main/resources/db/migration/V12__create_notification_tables.sql` (new)
- `src/main/java/com/novakai/orchestrator/domain/entity/NotificationSubscription.java` (new)
- `src/main/java/com/novakai/orchestrator/domain/entity/NotificationDeliveryLog.java` (new)
- `src/main/java/com/novakai/orchestrator/repository/NotificationSubscriptionRepository.java` (new)
- `src/main/java/com/novakai/orchestrator/repository/NotificationDeliveryLogRepository.java` (new)

**Definition of Done:**
- Migration creates both tables with proper foreign keys, indexes
- Entities use Lombok `@Entity`, `@Builder`, standard JPA annotations matching project conventions
- Repositories extend JpaRepository with custom query methods

**Test to Add:** Integration test that inserts a subscription and queries it back (uses H2 for test)

**Depends On:** Nothing (can run in parallel with Tasks 4-6)

---

## Task 8: Create JobRunCompletedEvent + RunCompletionPublisher service

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/notification/event/JobRunCompletedEvent.java` (new)
- `src/main/java/com/novakai/orchestrator/notification/event/RunCompletionPublisher.java` (new)
- `src/main/java/com/novakai/orchestrator/engine/JobExecutionOrchestrator.java` (modify)
- `src/main/java/com/novakai/orchestrator/engine/DagExecutionEngine.java` (modify)

**Definition of Done:**
- `RunCompletionPublisher` is a Spring service with `onComplete(JobRun, JobDefinition)` method
- Injected into both orchestrators; called after `runRepo.save(run)` in the finally/completion blocks
- Event contains runId, jobId, jobName, status, completedAt, triggeredBy

**Test to Add:** Unit test of RunCompletionPublisher — verify event published with correct fields. Update existing orchestrator tests to include a mock publisher.

**Depends On:** Task 7 (needs JobRun entity reference)

---

## Task 9: Create NotificationDispatcher with retry logic

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/notification/dispatch/NotificationDispatcher.java` (new)
- `src/main/java/com/novakai/orchestrator/engine/config/AsyncConfig.java` (modify — add notificationExecutor bean)

**Definition of Done:**
- `@EventListener(JobRunCompletedEvent.class)` method annotated with `@Async("notificationExecutor")`
- Resolves subscriptions matching jobId + event status
- Retry loop: 3 attempts, delays [1s, 5s, 25s]
- Writes to NOTIFICATION_DELIVERY_LOG at each attempt and on final success/failure
- `notificationExecutor` bean added to AsyncConfig (core=2, max=5, queue=100)

**Test to Add:** Unit test with mocked channel — verify retry count, verify delivery log entries. See Testing Plan for forced-failure test.

**Depends On:** Task 3, Task 7, Tasks 4-6 (all channels)

---

## Task 10: Create NotificationSubscription REST controller

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/controller/NotificationController.java` (new)
- `src/main/java/com/novakai/orchestrator/api/dto/NotificationSubscriptionRequest.java` (new)
- `src/main/java/com/novakai/orchestrator/api/dto/NotificationSubscriptionResponse.java` (new)
- `src/main/java/com/novakai/orchestrator/api/service/NotificationService.java` (new — service layer)

**Definition of Done:**
- `POST /api/jobs/{jobId}/notifications` — create subscription
- `GET /api/jobs/{jobId}/notifications` — list subscriptions for a job
- `PUT /api/jobs/{jobId}/notifications/{id}` — update subscription
- `DELETE /api/jobs/{jobId}/notifications/{id}` — delete subscription
- `GET /api/jobs/{jobId}/notifications/{id}/delivery-log` — delivery log for a subscription
- Service layer handles validation (channel_type must be registered, events must be valid RunStatus values)

**Test to Add:** Controller test with MockMvc — test all 4 CRUD endpoints + delivery log endpoint. Verify 404 on unknown jobId.

**Depends On:** Task 7, Task 3

---

## Task 11: Create ChannelConfigSchema API endpoint (for UI dynamic form)

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/controller/NotificationController.java` (modify — add endpoint)

**Definition of Done:**
- `GET /api/notification-channels` — returns list of registered channel types with their config schemas
- Enables the UI to render dynamic forms per channel type (same pattern as step config schema endpoint)

**Test to Add:** Controller test — verify all 3 channel schemas returned

**Depends On:** Task 10

---

## Task 12: Create NotificationSubscriptionFormComponent (Angular)

**Files Touched:**
- `orchestrator-ui/src/app/features/notifications/notification-subscription-form/` (new component)

**Definition of Done:**
- Channel type dropdown (populated from `/api/notification-channels`)
- Dynamic form fields based on selected channel's config schema (reuse `DynamicStepFormComponent` pattern — rename shared component to `DynamicConfigFormComponent` or import directly)
- Event multi-select (RUN_SUCCESS, RUN_FAILURE, RUN_PARTIAL, RUN_CANCELLED)
- Save/cancel wired to REST API

**Test to Add:** Component unit test — verify form renders fields for selected channel type

**Depends On:** Task 11

---

## Task 13: Create DeliveryLogComponent (Angular)

**Files Touched:**
- `orchestrator-ui/src/app/features/notifications/delivery-log/delivery-log.component.ts` (new)

**Definition of Done:**
- Table showing last 20 delivery attempts for a subscription
- Columns: Attempt #, Status (SENT/FAILED/RETRYING), Sent At, Error Message
- Color-coded status badges (green=SENT, red=FAILED, yellow=RETRYING)

**Test to Add:** Component unit test — verify table renders mock data correctly

**Depends On:** Task 10

---

## Task 14: Create NotificationsTabComponent and wire into job detail page

**Files Touched:**
- `orchestrator-ui/src/app/features/notifications/notifications-tab.component.ts` (new)
- Job detail routing/layout file (modify — add tab)

**Definition of Done:**
- Tab shows list of subscriptions for the current job with edit/delete actions
- "Add Notification" button opens SubscriptionFormComponent
- Delivery log accessible per subscription (click to expand or navigate)
- Tab integrated into existing job detail page tab bar

**Test to Add:** Integration test of the tab component — verify subscription list loads, add form works end-to-end

**Depends On:** Task 12, Task 13

---

## Task 15: Export/import support for notification subscriptions

**Files Touched:**
- `src/main/java/com/novakai/orchestrator/api/service/JobExportImportService.java` (modify)
- Import/Request DTOs (modify — add notifications field)

**Definition of Done:**
- Job export JSON includes an array of notification subscriptions
- Import creates subscriptions after job definition is restored
- Subscription references jobId by name (resolved after import), not by ID

**Test to Add:** Export/import round-trip test — export a job with 2 subscriptions, import into clean database, verify subscriptions match

**Depends On:** Task 10

---

## Task 16: End-to-end forced-failure integration test

**Files Touched:**
- `src/test/java/com/novakai/orchestrator/notification/NotificationDispatcherIntegrationTest.java` (new)

**Definition of Done:**
- Test creates a subscription with an unreachable webhook URL (`http://localhost:54321/nonexistent`)
- Publishes a `JobRunCompletedEvent`
- Verifies delivery log has exactly 3 attempts, all FAILED or last entry is FAILED
- Verifies Thread.sleep delays were observed (test takes >6 seconds total for retries)

**Test to Add:** This IS the test.

**Depends On:** Task 9
