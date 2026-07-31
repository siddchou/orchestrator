package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class ShellExecStepExecutorTest {

    private final ShellExecStepExecutor executor = new ShellExecStepExecutor(new JsonParser());

    @TempDir
    Path tempDir;

    @Test
    void getType_returns_SHELL_EXEC() {
        assertEquals("SHELL_EXEC", executor.getType());
    }

    @Test
    void getConfigSchema_has_command_and_scriptPath_fields() {
        StepConfigSchema schema = executor.getConfigSchema();
        assertNotNull(schema);
        assertEquals("SHELL_EXEC", schema.stepType());
        assertTrue(schema.fields().stream().anyMatch(f -> f.name().equals("command")));
        assertTrue(schema.fields().stream().anyMatch(f -> f.name().equals("scriptPath")));
    }

    @Test
    void getConfigSchema_has_timeout_default_300() {
        StepConfigSchema schema = executor.getConfigSchema();
        var timeoutField = schema.fields().stream()
            .filter(f -> f.name().equals("timeoutSeconds"))
            .findFirst()
            .orElseThrow();
        assertEquals(300, timeoutField.defaultValue());
    }

    @Test
    void execute_returns_failure_when_no_command_or_script() throws Exception {
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig("{}")
            .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess());
    }

    @Test
    void execute_returns_failure_when_config_null() throws Exception {
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig("")
            .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess());
    }

    @Test
    void execute_echo_command_succeeds() throws Exception {
        String config;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            config = "{\"command\":\"echo hello\"}";
        } else {
            config = "{\"command\":\"echo hello\"}";
        }
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.isSuccess());
        assertEquals(0, result.outputs().get("exitCode"));
    }

    @Test
    void execute_with_args() throws Exception {
        String config;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            config = "{\"command\":\"echo\",\"args\":[\"world\"]}";
        } else {
            config = "{\"command\":\"echo\",\"args\":[\"world\"]}";
        }
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_with_env_overrides() throws Exception {
        String config;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            config = "{\"command\":\"echo\",\"args\":[\"%TEST_VAR%\"],\"envOverrides\":{\"TEST_VAR\":\"hello\"}}";
        } else {
            config = "{\"command\":\"echo\",\"args\":[\"$TEST_VAR\"],\"envOverrides\":{\"TEST_VAR\":\"hello\"}}";
        }
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_with_working_directory() throws Exception {
        String config;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            config = "{\"command\":\"cd\",\"workingDirectory\":\"" + tempDir.toString().replace("\\", "/") + "\"}";
        } else {
            config = "{\"command\":\"pwd\",\"workingDirectory\":\"" + tempDir.toFile().getPath() + "\"}";
        }
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_script_path_succeeds() throws Exception {
        Path script;
        String configTemplate;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            script = tempDir.resolve("test.bat");
            Files.writeString(script, "echo hello\r\n");
            configTemplate = "{\"scriptPath\":\"%s\"}";
        } else {
            script = tempDir.resolve("test.sh");
            Files.writeString(script, "#!/bin/sh\necho hello\n");
            script.toFile().setExecutable(true);
            configTemplate = "{\"scriptPath\":\"%s\"}";
        }

        String config = String.format(configTemplate, script.toString().replace("\\", "/"));
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_timeout_kills_long_running_command() throws Exception {
        String config;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            config = "{\"command\":\"timeout\",\"args\":[\"999\"],\"timeoutSeconds\":2}";
        } else {
            config = "{\"command\":\"sleep\",\"args\":[\"999\"],\"timeoutSeconds\":2}";
        }
        var ctx = StepContext.builder()
            .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
            .stepConfig(config)
            .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess());
    }
}
