<!-- FILE: phase5-05-testing-plan.md -->
# Phase 5 — Testing Plan

## Unit Tests — Per Channel Implementation

### EmailNotificationChannelTest

| Test case | Setup | Assertion |
|-----------|-------|-----------|
| Sends email with correct subject/body | Mock `JavaMailSender`, valid config with recipients | `verify(mailSender).send(msg)` — subject matches `[SUCCESS] JobName completed`, body contains runId, status, jobName |
| Uses fromAddress override | Config has `fromAddress` field | Message.from equals the override value |
| Throws on null recipients list | Config missing `recipients` field | `NotificationException` thrown with message mentioning "recipients" |
| Schema returns required fields | Call `getConfigSchema()` | Returns schema with `recipients` marked required, `fromAddress` optional |

### SlackWebhookChannelTest

| Test case | Setup | Assertion |
|-----------|-------|-----------|
| Posts Block Kit payload to webhook URL | Mock `RestTemplate`, return 200 OK | `verify(restTemplate).postForEntity(eq("https://hooks.slack.com/..."), any(), eq(String.class))` — payload contains `"blocks"` array |
| Throws on 4xx response | Mock `RestTemplate`, return 403 Forbidden | `NotificationException` thrown with "Slack returned 403" |
| Throws on 5xx response | Mock `RestTemplate`, return 500 | `NotificationException` thrown |
| Payload includes status emoji mapping | Event with SUCCESS/FAILED/PARTIAL/CANCELLED | Header block text contains the status value |

### GenericWebhookChannelTest

| Test case | Setup | Assertion |
|-----------|-------|-----------|
| Default POST with template resolution | Config has `webhookUrl` and `payload: {"status":"{{status}}"}` | Request body is `{"status":"SUCCESS"}` (resolved) |
| Custom HTTP method (PUT) | Config has `method: "PUT"` | `restTemplate.exchange()` called with `HttpMethod.PUT` |
| Custom headers applied | Config has `headers: {"Authorization":"Bearer tok"}` | Request entity has the header |
| Unknown template variable replaced with empty string | Payload has `{{unknownField}}` | Resolved to `""`, no exception thrown, debug log emitted |
| Throws on non-2xx response | Mock returns 502 | `NotificationException` thrown |

### NotificationChannelRegistryTest

| Test case | Setup | Assertion |
|-----------|-------|-----------|
| Registers channels from constructor injection | Pass list of 3 mock channels | All 3 accessible via `get(type)` |
| Duplicate type logs warning and replaces | Register two channels with same type | Second replaces first; `log.warn` called (verify with `@CatchEvent`) |
| Lookup for unknown type returns empty | `get("NONEXISTENT")` | Returns `Optional.empty()` |
| listAll returns all schemas | 3 registered channels | Returns list of size 3 |
| registeredTypes returns unmodifiable set | 3 registered channels | Set has 3 entries; modification throws `UnsupportedOperationException` |

## Unit Tests — Dispatcher

### NotificationDispatcherTest

| Test case | Setup | Assertion |
|-----------|-------|-----------|
| Dispatches to matching subscriptions only | 2 subscriptions: one for SUCCESS, one for FAILED. Event is SUCCESS. | Only SUCCESS subscription's channel.send() called |
| Retry loop retries on exception | Mock channel throws twice then succeeds on 3rd attempt | `channel.send()` called exactly 3 times; delivery log status = SENT, attempt_count = 3 |
| Marks FAILED after exhausting retries | Mock channel always throws | Delivery log: status=FAILED, attempt_count=3, last_error set |
| Handles missing channel type gracefully | Subscription has channel_type "SMS" (not registered) | No exception thrown; delivery log: status=FAILED with message about unregistered type |
| Async execution doesn't block caller | Call `onRunCompleted()` and check immediately | Method returns immediately (async); use `CountDownLatch` in test to confirm channel.send() was called |

## Integration Tests

### NotificationDispatcherIntegrationTest (forced-failure test)

**This is the key integration test that proves retry + delivery log end-to-end.**

```java
@ExtendWith(SpringExtension.class)
@DataJpaTest(
    properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.flyway.enabled=false"
    }
)
@Import({
    NotificationDispatcher.class,
    NotificationChannelRegistry.class,
    GenericWebhookChannel.class,
    AsyncConfig.class,
    RunCompletionPublisher.class
})
class NotificationDispatcherIntegrationTest {

    @Autowired private NotificationSubscriptionRepository subRepo;
    @Autowired private NotificationDeliveryLogRepository logRepo;
    @Autowired private ApplicationEventPublisher publisher;

    @Test
    void failedWebhookRetriesThreeTimesThenMarksFailed() throws InterruptedException {
        // 1. Create subscription pointing to unreachable URL
        var sub = NotificationSubscription.builder()
            .jobDefinitionId(999L)
            .channelType("GENERIC_WEBHOOK")
            .channelConfig("{\"webhookUrl\":\"http://localhost:54321/nonexistent\",\"payload\":\"{}\"}")
            .events("SUCCESS,FAILED")
            .build();
        subRepo.save(sub);

        // 2. Publish event
        publisher.publishEvent(new JobRunCompletedEvent(
            null, 1L, 999L, "TestJob", RunStatus.SUCCESS,
            LocalDateTime.now(), "test-user"));

        // 3. Wait for retries to complete (1s + 5s backoff = ~6-7 seconds)
        Thread.sleep(8000);

        // 4. Verify delivery log
        List<NotificationDeliveryLog> logs = logRepo.findBySubscriptionId(sub.getId());
        assertThat(logs).hasSize(1);  // one log entry, updated in place
        NotificationDeliveryLog logEntry = logs.get(0);
        assertThat(logEntry.getStatus()).isEqualTo("FAILED");
        assertThat(logEntry.getAttemptCount()).isEqualTo(3);
        assertThat(logEntry.getLastError()).contains("Connect")
            .orContains("connection refused").orContains("54321");
        assertThat(logEntry.getSentAt()).isNull();  // never succeeded
    }

    @Test
    void successfulEmailMarksSent() {
        // Use a mock mail server (GreenMail or similar) or mock the channel
        // Simpler: test with a webhook that points to a local HTTP server
        // that returns 200.
    }
}
```

**Expected behavior:** The test takes ~7-8 seconds due to retry backoff delays. Delivery log shows exactly 1 entry (created on first attempt, updated after each retry) with final status `FAILED`, `attempt_count=3`.

### RunCompletionPublisherIntegrationTest

| Test case | Setup | Assertion |
|-----------|-------|-----------|
| Event published when orchestrator completes | Mock orchestrator with real publisher and event listener spy | `JobRunCompletedEvent` captured by spy, contains correct runId/status |
| Both linear and DAG paths publish the event | Test both `execute()` and `DagExecutionEngine.completeRun()` paths | Event fired in both cases |

### NotificationControllerIntegrationTest (MockMvc)

| Test case | Setup | Assertion |
|-----------|-------|-----------|
| POST creates subscription | Valid JSON body, existing jobId | 201 Created, response matches request |
| POST rejects invalid channel_type | `channelType: "NONEXISTENT"` | 400 Bad Request |
| POST rejects missing required config field | Generic webhook without `webhookUrl` | 400 with field error |
| GET lists subscriptions for job | 2 saved subscriptions | Response array has 2 items |
| PUT updates subscription events | Valid update body | 200 OK, events changed |
| DELETE removes subscription | Existing subscription ID | 204 No Content, subsequent GET returns empty |
| Delivery log endpoint returns entries | Subscription with delivery log entries | Array of log entries returned |

## Test coverage summary

| Component | Unit Tests | Integration Tests | Total |
|-----------|-----------|-------------------|-------|
| EmailNotificationChannel | 4 | — | 4 |
| SlackWebhookChannel | 4 | — | 4 |
| GenericWebhookChannel | 5 | — | 5 |
| NotificationChannelRegistry | 5 | — | 5 |
| NotificationDispatcher | 5 | 2 (forced-failure + success) | 7 |
| RunCompletionPublisher | — | 2 | 2 |
| NotificationController | — | 7 | 7 |
| **Total** | **18** | **11** | **29** |
