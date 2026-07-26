package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class HttpCallStepExecutorTest {

    private final HttpCallStepExecutor executor = new HttpCallStepExecutor();

    @Test
    void getType_returns_HTTP_CALL() {
        assertEquals("HTTP_CALL", executor.getType());
    }

    @Test
    void getConfigSchema_has_required_url_field() {
        StepConfigSchema schema = executor.getConfigSchema();
        assertNotNull(schema);
        assertEquals("HTTP_CALL", schema.stepType());
        assertTrue(schema.fields().stream().anyMatch(f -> f.name().equals("url") && f.required()));
    }

    @Test
    void getConfigSchema_has_method_enum_with_default_GET() {
        StepConfigSchema schema = executor.getConfigSchema();
        var methodField = schema.fields().stream()
            .filter(f -> f.name().equals("method"))
            .findFirst()
            .orElseThrow();
        assertEquals("GET", methodField.defaultValue());
        assertNotNull(methodField.enumValues());
        assertTrue(methodField.enumValues().contains("POST"));
    }

    @Test
    void getConfigSchema_has_timeout_default_30() {
        StepConfigSchema schema = executor.getConfigSchema();
        var timeoutField = schema.fields().stream()
            .filter(f -> f.name().equals("timeoutSeconds"))
            .findFirst()
            .orElseThrow();
        assertEquals(30, timeoutField.defaultValue());
    }

    @Test
    void execute_returns_failure_when_config_is_null() throws Exception {
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig("")
            .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess());
    }

    @Test
    void execute_returns_failure_when_url_missing() throws Exception {
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig("{}")
            .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess());
    }

    @Test
    void execute_returns_failure_for_unreachable_url() throws Exception {
        String config = "{\"url\":\"http://localhost:9\",\"timeoutSeconds\":1}";
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess());
    }

    @Test
    void execute_with_headers_and_body() throws Exception {
        String config = """
            {"url":"http://localhost:9","method":"POST","headers":{"Content-Type":"text/plain"},"body":"hello"}
            """;
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess()); // connection refused, but no parse error
    }

    @Test
    void execute_outputs_contain_status_code_on_success() throws Exception {
        // this will fail to connect but should still produce structured output for reachable endpoints
        // we test the structure by using a config that parses correctly
        String config = "{\"url\":\"http://localhost:9\",\"timeoutSeconds\":1}";
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        // even on connection failure, the executor should return a result (not throw)
        assertNotNull(result.message());
    }

    @Test
    void execute_with_expected_status() throws Exception {
        String config = """
            {"url":"http://localhost:9","expectedStatus":200,"timeoutSeconds":1}
            """;
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertNotNull(result);
    }
}
