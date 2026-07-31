package com.novakai.orchestrator.engine.spi;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StepContextTest {

    @Test
    void builder_builds_context_with_all_fields() {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        Map<String, Object> params = Map.of("key", "value");
        Path workDir = Path.of("/tmp/work");

        StepContext ctx = StepContext.builder()
                .runId(1L)
                .jobId(42L)
                .stepId("step-1")
                .stepConfig("{\"command\":\"echo hi\"}")
                .resolvedParams(params)
                .credentials(ref -> "secret-" + ref)
                .logSink(new StepContext.LogSink(queue))
                .workDir(workDir)
                .javaHome("/usr/lib/jvm/java-21")
                .classpath(List.of("a.jar", "b.jar"))
                .envVars(Map.of("PATH", "/usr/bin"))
                .build();

        assertEquals(1L, ctx.getRunId());
        assertEquals(42L, ctx.getJobId());
        assertEquals("step-1", ctx.getStepId());
        assertEquals("{\"command\":\"echo hi\"}", ctx.getStepConfig());
        assertEquals("value", ctx.getResolvedParams().get("key"));
        assertEquals("secret-my-ref", ctx.getCredentials().resolve("my-ref"));
        assertSame(queue, ctx.getLogSink().getQueue());
        assertEquals(workDir, ctx.getWorkDir());
        assertEquals("/usr/lib/jvm/java-21", ctx.getJavaHome());
        assertEquals(List.of("a.jar", "b.jar"), ctx.getClasspath());
        assertEquals("/usr/bin", ctx.getEnvVars().get("PATH"));
    }

    @Test
    void getLiveLogQueue_returns_queue_from_log_sink() {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        StepContext ctx = StepContext.builder()
                .logSink(new StepContext.LogSink(queue))
                .build();
        assertSame(queue, ctx.getLiveLogQueue());
    }

    @Test
    void log_sink_log_adds_to_queue() {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        StepContext.LogSink sink = new StepContext.LogSink(queue);

        sink.log("hello world");

        assertEquals(1, queue.size());
        assertEquals("hello world", queue.poll());
    }

    @Test
    void log_sink_log_null_queue_no_op() {
        StepContext.LogSink sink = new StepContext.LogSink(null);
        assertDoesNotThrow(() -> sink.log("should not crash"));
    }

    @Test
    void log_sink_log_blank_line_ignored() {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        StepContext.LogSink sink = new StepContext.LogSink(queue);

        sink.log("");
        sink.log("   ");

        assertTrue(queue.isEmpty());
    }

    @Test
    void setJavaHome_mutates_java_home() {
        StepContext ctx = StepContext.builder().javaHome("/old").build();
        assertEquals("/old", ctx.getJavaHome());

        ctx.setJavaHome("/new");
        assertEquals("/new", ctx.getJavaHome());
    }

    @Test
    void env_vars_isolation_from_builder() {
        Map<String, String> original = new HashMap<>();
        original.put("KEY", "original");

        StepContext ctx = StepContext.builder().envVars(original).build();
        ctx.getEnvVars().put("NEW_KEY", "added");

        // Mutating ctx's envVars should not affect the builder's map
        assertFalse(original.containsKey("NEW_KEY"));
    }

    @Test
    void cancel_requested_volatile_flag_across_threads() throws InterruptedException {
        StepContext ctx = StepContext.builder().build();
        assertFalse(ctx.isCancelRequested());

        Thread setter = new Thread(() -> ctx.setCancelRequested(true));
        setter.start();

        // Spin-wait for flag to become true (volatile guarantees visibility)
        boolean observed = false;
        for (int i = 0; i < 100; i++) {
            if (ctx.isCancelRequested()) {
                observed = true;
                break;
            }
            Thread.sleep(1);
        }
        setter.join(2000);

        assertTrue(observed, "cancelRequested should be visible across threads via volatile");
    }
}
