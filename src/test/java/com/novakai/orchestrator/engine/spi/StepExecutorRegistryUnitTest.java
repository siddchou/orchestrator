package com.novakai.orchestrator.engine.spi;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StepExecutorRegistry that don't require Spring context.
 */
class StepExecutorRegistryUnitTest {

    private static final class MockExecutor implements StepExecutor {
        private final String type;

        MockExecutor(String type) { this.type = type; }

        @Override public String getType() { return type; }

        @Override public StepConfigSchema getConfigSchema() {
            return new StepConfigSchema(type, "Mock: " + type, List.of());
        }

        @Override public StepResult execute(StepContext ctx) { return StepResult.success(Map.of(), "", java.time.Duration.ZERO); }
    }

    @Test
    void register_distinct_types_all_resolve() {
        StepExecutorRegistry registry = new StepExecutorRegistry(List.of());
        registry.register(new MockExecutor("A"));
        registry.register(new MockExecutor("B"));
        registry.register(new MockExecutor("C"));

        assertTrue(registry.get("A").isPresent());
        assertTrue(registry.get("B").isPresent());
        assertTrue(registry.get("C").isPresent());
    }

    @Test
    void register_duplicate_type_warns_and_last_wins() {
        // Capture log output
        Logger logger = (Logger) LoggerFactory.getLogger(StepExecutorRegistry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);

        StepExecutorRegistry registry = new StepExecutorRegistry(List.of());
        MockExecutor first = new MockExecutor("DUPE");
        MockExecutor second = new MockExecutor("DUPE");
        registry.register(first);
        registry.register(second);

        // Last registered wins
        assertInstanceOf(MockExecutor.class, registry.get("DUPE").orElse(null));

        // Warning was logged
        List<ILoggingEvent> events = appender.list;
        assertTrue(events.stream().anyMatch(e -> e.getMessage() != null && e.getMessage().contains("Duplicate executor")),
                "Should log duplicate warning: " + events);
    }

    @Test
    void get_unregistered_type_returns_empty() {
        StepExecutorRegistry registry = new StepExecutorRegistry(List.of());
        assertFalse(registry.get("NONEXISTENT").isPresent());
    }

    @Test
    void listAll_returns_schema_for_every_registered_executor() {
        StepExecutorRegistry registry = new StepExecutorRegistry(List.of());
        registry.register(new MockExecutor("X"));
        registry.register(new MockExecutor("Y"));

        var schemas = registry.listAll();
        assertEquals(2, schemas.size());
    }

    @Test
    void registeredTypes_returns_all_type_strings() {
        StepExecutorRegistry registry = new StepExecutorRegistry(List.of());
        registry.register(new MockExecutor("P"));
        registry.register(new MockExecutor("Q"));

        Set<String> types = registry.registeredTypes();
        assertEquals(Set.of("P", "Q"), types);
    }

    @Test
    void concurrent_reads_from_twenty_threads() throws InterruptedException {
        StepExecutorRegistry registry = new StepExecutorRegistry(List.of());
        for (int i = 0; i < 10; i++) {
            registry.register(new MockExecutor("TYPE_" + i));
        }

        int numThreads = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        for (int t = 0; t < numThreads; t++) {
            pool.submit(() -> {
                try {
                    startLatch.await(10, TimeUnit.SECONDS);
                    // Each thread does multiple reads
                    for (int i = 0; i < 50; i++) {
                        String type = "TYPE_" + (i % 10);
                        var result = registry.get(type);
                        if (i < 10) {
                            assertTrue(result.isPresent(), "Should find " + type);
                        }
                        // Also exercise listAll and registeredTypes
                        registry.listAll();
                        registry.registeredTypes();
                    }
                } catch (Throwable ex) {
                    errors.add(ex);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // release all threads at once
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Threads should complete");
        pool.shutdown();

        if (!errors.isEmpty()) {
            fail("Concurrent reads threw errors: " + errors);
        }
    }
}
