package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.StepType;
import com.novakai.orchestrator.engine.ExecutionContext;
import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.StepResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LogCleanupStepExecutorTest {

    private final LogCleanupStepExecutor executor = new LogCleanupStepExecutor(new JsonParser());

    @TempDir
    Path tempDir;

    private String toFsPath(String path) {
        return path.replace("\\", "/");
    }

    @Test
    void getSupportedType_returns_LOG_CLEANUP() {
        assertEquals(StepType.LOG_CLEANUP, executor.getSupportedType());
    }

    @Test
    void execute_returns_failure_when_config_is_null() throws Exception {
        JobStep step = JobStep.builder().stepConfig("").build();
        ExecutionContext ctx = ExecutionContext.builder()
                .workingDir(toFsPath(tempDir.toString()))
                .envVars(Map.of())
                .build();

        StepResult result = executor.execute(ctx, step);

        assertFalse(result.success());
        assertTrue(result.logOutput().contains("null or empty"));
    }

    @Test
    void execute_returns_failure_when_directory_does_not_exist() throws Exception {
        String config = """
                {"directory":"/nonexistent/path","filePattern":"*.log"}
                """;
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = ExecutionContext.builder()
                .workingDir(toFsPath(tempDir.toString()))
                .envVars(Map.of())
                .build();

        StepResult result = executor.execute(ctx, step);

        assertFalse(result.success());
        assertTrue(result.logOutput().contains("does not exist"));
    }

    @Test
    void execute_deletes_matching_files() throws Exception {
        Files.writeString(tempDir.resolve("app.log"), "log data");
        Files.writeString(tempDir.resolve("error.log"), "error data");
        Files.writeString(tempDir.resolve("data.txt"), "keep this");

        String dir = toFsPath(tempDir.toString());
        String config = String.format(
                "{\"directory\":\"%s\",\"filePattern\":\"*.log\"}",
                dir
        );
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = ExecutionContext.builder()
                .workingDir(dir)
                .envVars(Map.of())
                .build();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
        assertTrue(result.logOutput().contains("Total files deleted: 2"));
        assertFalse(Files.exists(tempDir.resolve("app.log")));
        assertFalse(Files.exists(tempDir.resolve("error.log")));
        assertTrue(Files.exists(tempDir.resolve("data.txt")));
    }

    @Test
    void execute_deletes_zero_files_when_no_match() throws Exception {
        Files.writeString(tempDir.resolve("data.txt"), "content");

        String dir = toFsPath(tempDir.toString());
        String config = String.format(
                "{\"directory\":\"%s\",\"filePattern\":\"*.log\"}",
                dir
        );
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = ExecutionContext.builder()
                .workingDir(dir)
                .envVars(Map.of())
                .build();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
        assertTrue(result.logOutput().contains("Total files deleted: 0"));
        assertTrue(Files.exists(tempDir.resolve("data.txt")));
    }

    @Test
    void execute_with_relative_directory() throws Exception {
        Path subDir = tempDir.resolve("logs");
        Files.createDirectory(subDir);
        Files.writeString(subDir.resolve("old.log"), "old");

        String workingDir = toFsPath(tempDir.toString());
        String config = String.format(
                "{\"directory\":\"logs\",\"filePattern\":\"*.log\"}",
                workingDir
        );
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = ExecutionContext.builder()
                .workingDir(workingDir)
                .envVars(Map.of())
                .build();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
        assertTrue(result.logOutput().contains("Deleted: old.log"));
        assertFalse(Files.exists(subDir.resolve("old.log")));
    }

    @Test
    void execute_deletes_all_matching_with_wildcard() throws Exception {
        for (int i = 0; i < 5; i++) {
            Files.writeString(tempDir.resolve("app-" + i + ".log"), "log " + i);
        }

        String dir = toFsPath(tempDir.toString());
        String config = String.format(
                "{\"directory\":\"%s\",\"filePattern\":\"app-*.log\"}",
                dir
        );
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = ExecutionContext.builder()
                .workingDir(dir)
                .envVars(Map.of())
                .build();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
        assertTrue(result.logOutput().contains("Total files deleted: 5"));
    }
}
