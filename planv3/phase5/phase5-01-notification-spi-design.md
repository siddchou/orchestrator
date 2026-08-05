<!-- FILE: phase5-01-notification-spi-design.md -->
# Phase 5 — Notification SPI Design

## 1. NotificationChannel Interface

```java
package com.novakai.orchestrator.notification.spi;

/**
 * Pluggable notification delivery channel.
 * Mirrors StepExecutor SPI: getType() identifies the channel, send() delivers.
 */
public interface NotificationChannel {
    /** Unique type string: "EMAIL", "SLACK_WEBHOOK", "GENERIC_WEBHOOK" */
    String getType();

    /**
     * Deliver a notification event using the provided channel configuration.
     * Implementations should be idempotent — the dispatcher retries on failure.
     */
    void send(NotificationEvent event, ChannelConfig config) throws NotificationException;

    /** Config schema for the UI dynamic form (same pattern as StepConfigSchema). */
    ChannelConfigSchema getConfigSchema();
}
```

### Supporting types

```java
public record NotificationEvent(
    Long runId,
    Long jobId,
    String jobName,
    RunStatus status,        // SUCCESS, FAILED, PARTIAL, CANCELLED
    LocalDateTime completedAt,
    String triggeredBy       // user who launched the run
) {}

public record ChannelConfig(
    Map<String, Object> params  // JSON-backed: {"webhookUrl":"..."}, {"recipients":["a@b.com"]}
) {
    public String getString(String key) { ... }
    public List<String> getList(String key) { ... }
}

public record ChannelConfigSchema(
    String type,
    FieldDefinition[] fields     // reuse the same record from StepExecutor SPI
) {}

public class NotificationException extends RuntimeException {
    // checked-style but unchecked for simplicity; dispatcher catches and retries
}
```

## 2. Three Implementations

### EmailNotificationChannel (`getType() = "EMAIL"`)

```java
@Component
public class EmailNotificationChannel implements NotificationChannel {
    private final JavaMailSender mailSender;

    public void send(NotificationEvent event, ChannelConfig config) {
        String from = config.getString("fromAddress");  // or use spring.mail.properties.from
        List<String> to = config.getList("recipients");
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to.toArray(new String[0]));
        msg.setSubject(String.format("[%s] Job %s completed", event.status(), event.jobName()));
        msg.setText(buildBody(event));
        mailSender.send(msg);
    }
}
```

**Config schema fields:**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `recipients` | string[] | Yes | Comma-separated email addresses |
| `fromAddress` | string | No | Override spring.mail.properties.from |

**Dependency to add:** `spring-boot-starter-mail` + runtime mail transport (e.g., `jakarta.mail`)

### SlackWebhookChannel (`getType() = "SLACK_WEBHOOK"`)

```java
@Component
public class SlackWebhookChannel implements NotificationChannel {
    private final RestTemplate restTemplate;  // or WebClient

    public void send(NotificationEvent event, ChannelConfig config) {
        String url = config.getString("webhookUrl");
        SlackPayload payload = new SlackPayload(buildBlockKitMessage(event));
        ResponseEntity<String> response = restTemplate.postForEntity(url, payload, String.class);
        if (!response.getStatusCode().is2xxSuccessful())
            throw new NotificationException("Slack returned " + response.getStatusCode());
    }
}
```

**Config schema fields:**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `webhookUrl` | string | Yes | Slack Incoming Webhook URL |
| `channel` | string | No | Override webhook default channel |

**Slack Block Kit payload structure:**
```json
{
  "blocks": [
    {"type": "header", "text": {"type": "plain_text", "text": "Job Run: SUCCESS"}},
    {"type": "section", "fields": [...]}
  ]
}
```

### GenericWebhookChannel (`getType() = "GENERIC_WEBHOOK"`)

```java
@Component
public class GenericWebhookChannel implements NotificationChannel {
    private final RestTemplate restTemplate;

    public void send(NotificationEvent event, ChannelConfig config) {
        String url = config.getString("webhookUrl");
        String method = config.getString("method");  // POST (default), PUT
        String headersJson = config.getString("headers");  // optional JSON map of headers
        String payloadTemplate = config.getString("payload"); // JSON template with variable placeholders

        Map<String, Object> resolved = resolveTemplate(payloadTemplate, event);
        HttpEntity<Map<String, Object>> request = buildRequest(resolved, headersJson);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.valueOf(method), request, String.class);

        if (!response.getStatusCode().is2xxSuccessful())
            throw new NotificationException("Webhook returned " + response.getStatusCode());
    }
}
```

**Config schema fields:**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `webhookUrl` | string | Yes | Target URL |
| `method` | string | No | HTTP method (default: POST) |
| `headers` | object | No | JSON map of extra headers |
| `payload` | string | No | JSON payload template (`{{jobName}}`, `{{status}}`, etc.) |

**Template resolution:** Replace `{{fieldName}}` with the corresponding `NotificationEvent` field value via reflection or explicit mapping.

## 3. NotificationChannelRegistry

**Explicit design inheritance from StepExecutorRegistry:**

| Pattern element | StepExecutorRegistry | NotificationChannelRegistry |
|-----------------|---------------------|----------------------------|
| Storage | `ConcurrentHashMap<String, StepExecutor>` | `ConcurrentHashMap<String, NotificationChannel>` |
| Constructor injection | `List<StepExecutor>` auto-wired | `List<NotificationChannel>` auto-wired |
| Duplicate detection | `log.warn()` + replace | **Same:** `log.warn()` + replace |
| Lookup return type | `Optional<StepExecutor>` | **Same:** `Optional<NotificationChannel>` |
| Miss logging | `log.debug("No executor...")` | **Same:** `log.debug("No channel...")` |
| List all | `listAll()` returns schemas | `listAll()` returns schemas |
| Registered types | `registeredTypes()` returns key set | `registeredTypes()` returns key set |

```java
@Component
@Slf4j
public class NotificationChannelRegistry {
    private final Map<String, NotificationChannel> channelMap = new ConcurrentHashMap<>();
    private final List<ChannelConfigSchema> schemas = Collections.synchronizedList(new ArrayList<>());

    public NotificationChannelRegistry(List<NotificationChannel> channels) {
        for (NotificationChannel c : channels) {
            register(c);
        }
    }

    public void register(NotificationChannel channel) {
        String type = channel.getType();
        NotificationChannel previous = this.channelMap.put(type, channel);
        if (previous != null) {
            log.warn("Duplicate notification channel for type '{}': {} replaces {}",
                type, channel.getClass().getSimpleName(), previous.getClass().getSimpleName());
        }
        this.schemas.add(channel.getConfigSchema());
    }

    public Optional<NotificationChannel> get(String type) {
        NotificationChannel channel = channelMap.get(type);
        if (channel == null) {
            log.debug("No notification channel registered for type: {}", type);
            return Optional.empty();
        }
        return Optional.of(channel);
    }

    public List<ChannelConfigSchema> listAll() { return new ArrayList<>(schemas); }
    public Set<String> registeredTypes() { return Collections.unmodifiableSet(channelMap.keySet()); }
}
```

## 4. JobRunCompletedEvent (Spring ApplicationEvent)

Since no custom events exist in the codebase, this introduces the first one:

```java
public record JobRunCompletedEvent(
    Object source,
    Long runId,
    Long jobId,
    String jobName,
    RunStatus status,
    LocalDateTime completedAt,
    String triggeredBy
) extends ApplicationEvent {
    public JobRunCompletedEvent(JobRun run, JobDefinition job) {
        super(run);
        // map fields from entities
    }
}
```

### Hook points — where to publish the event

The event must be published after `runRepo.save(run)` in **both** execution paths:

1. **JobExecutionOrchestrator.execute()** — line 97, after `runRepo.save(run)` in the finally block
2. **JobExecutionOrchestrator.executeSingleStep()** — line 121, same location
3. **DagExecutionEngine.completeRun()** — line 513, after `runRepo.save(run)`

**Approach:** Inject `ApplicationEventPublisher` into both `JobExecutionOrchestrator` and `DagExecutionEngine`. Publish the event as the last statement before the method returns in each finally/completion block.

Alternatively (cleaner): Create a `RunCompletionListener` component that the orchestrators call, encapsulating the publish logic. This avoids duplicating the event construction across 3 sites.

**Recommended:** Extract a private helper method `publishRunCompleted(JobRun run, JobDefinition job)` in each class, or better yet, a shared `RunCompletionPublisher` service with a single `onComplete(run, job)` method injected into both orchestrators.

## 5. NotificationDispatcher

```java
@Component
@Slf4j
public class NotificationDispatcher {

    private final NotificationSubscriptionRepository subscriptionRepo;
    private final NotificationChannelRegistry channelRegistry;
    private final NotificationDeliveryLogRepository deliveryLogRepo;
    private final JsonParser jsonParser;

    @EventListener
    @Async("notificationExecutor")   // uses dedicated thread pool from AsyncConfig
    public void onRunCompleted(JobRunCompletedEvent event) {
        List<NotificationSubscription> subscriptions =
            subscriptionRepo.findByJobDefinitionIdAndEventsContaining(
                event.jobId(), event.status().name());

        for (NotificationSubscription sub : subscriptions) {
            dispatch(event, sub);
        }
    }

    private void dispatch(JobRunCompletedEvent event, NotificationSubscription sub) {
        NotificationDeliveryLog logEntry = createPendingLog(sub, event.runId());

        Optional<NotificationChannel> channelOpt = channelRegistry.get(sub.getChannelType());
        if (channelOpt.isEmpty()) {
            markFailed(logEntry, "No channel registered for type: " + sub.getChannelType());
            return;
        }

        NotificationChannel channel = channelOpt.get();
        ChannelConfig config = parseConfig(sub.getChannelConfig());

        // Retry loop: 3 attempts, exponential backoff (1s, 5s, 25s)
        int maxAttempts = 3;
        Duration[] delays = {Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(25)};

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                channel.send(toNotificationEvent(event), config);
                markSent(logEntry, attempt);
                return;
            } catch (Exception ex) {
                log.warn("Notification attempt {}/{ } failed for subscription {}: {}",
                    attempt, maxAttempts, sub.getId(), ex.getMessage());
                updateRetrying(logEntry, attempt, ex.getMessage());
                if (attempt < maxAttempts) {
                    try { Thread.sleep(delays[attempt - 1].toMillis()); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }

        // All retries exhausted
        markFailed(logEntry, "Exhausted " + maxAttempts + " attempts. Last: " +
            logEntry.getLastError());
    }
}
```

### Retry/backoff parameters

| Attempt | Delay before retry | Delivery log status |
|---------|-------------------|---------------------|
| 1 (initial) | — | `PENDING` → try |
| 2 | 1 second | `RETRYING`, attempt_count=1 |
| 3 | 5 seconds | `RETRYING`, attempt_count=2 |
| Exhausted | — | `FAILED`, attempt_count=3 |
| Success at any attempt | N/A | `SENT`, sent_at=now |

## 6. AsyncConfig addition

Add to existing `AsyncConfig.java`:

```java
@Bean(name = "notificationExecutor")
public ThreadPoolTaskExecutor notificationExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(5);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("notify-");
    executor.setRejectedExecutionHandler(
        new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
    executor.setTaskDecorator(runnable -> { /* same MDC decorator */ });
    executor.initialize();
    return executor;
}
```

Small pool: notifications are I/O-bound (HTTP calls, SMTP) and should not starve job execution threads. CallerRunsPolicy ensures back-pressure if the queue fills — the calling (run-completion) thread will deliver a notification synchronously rather than dropping it.
