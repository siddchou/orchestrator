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

class ArchiveStepExecutorTest {

    private final ArchiveStepExecutor executor = new ArchiveStepExecutor(new JsonParser());

    @TempDir
    Path tempDir;

    private String toFsPath(String path) {
        return path.replace("\\", "/");
    }

    @Test
    void getSupportedType_returns_ARCHIVE() {
        assertEquals(StepType.ARCHIVE, executor.getSupportedType());
    }

    @Test
    void execute_returns_failure_when_config_is_null() throws Exception {
        JobStep step = JobStep.builder().stepConfig("").build();
        ExecutionContext ctx = ExecutionContext.builder()
                .workingDir(toFsPath(tempDir.toString()))
                .envVars(Map.of("JOB_NAME", "test"))
                .build();

        StepResult result = executor.execute(ctx, step);

        assertFalse(result.success());
        assertTrue(result.logOutput().contains("null or empty"));
    }

    @Test
    void execute_creates_zip_archive() throws Exception {
        Files.writeString(tempDir.resolve("test1.txt"), "hello");
        Files.writeString(tempDir.resolve("test2.txt"), "world");

        String sourceDir = toFsPath(tempDir.toString());
        String archiveDir = toFsPath(tempDir.resolve("archives").toString());
        String config = String.format(
                "{\"sourceDir\":\"%s\",\"filePatterns\":[\"*.txt\"],\"archiveDir\":\"%s\",\"archiveFormat\":\"ZIP\"}",
                sourceDir, archiveDir
        );
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = ExecutionContext.builder()
                .workingDir(sourceDir)
                .envVars(Map.of("JOB_NAME", "testjob"))
                .build();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
        assertTrue(result.logOutput().contains("Archive created:"));
        Path archiveDirPath = Path.of(archiveDir);
        var archives = Files.list(archiveDirPath).toList();
        assertFalse(archives.isEmpty());
        assertTrue(archives.stream().anyMatch(p -> p.toString().endsWith(".zip")));
    }

    @Test
    void execute_creates_tar_gz_archive() throws Exception {
        Files.writeString(tempDir.resolve("data1.csv"), "col1,col2");

        String sourceDir = toFsPath(tempDir.toString());
        String archiveDir = toFsPath(tempDir.resolve("tgz").toString());
        String config = String.format(
                "{\"sourceDir\":\"%s\",\"filePatterns\":[\"*.csv\"],\"archiveDir\":\"%s\",\"archiveFormat\":\"TAR_GZ\"}",
                sourceDir, archiveDir
        );
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = ExecutionContext.builder()
                .workingDir(sourceDir)
                .envVars(Map.of("JOB_NAME", "csvjob"))
                .build();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
        assertTrue(result.logOutput().contains("Archive created:"));
        Path archiveDirPath = Path.of(archiveDir);
        var archives = Files.list(archiveDirPath).toList();
        assertTrue(archives.stream().anyMatch(p -> p.toString().endsWith(".tar.gz")));
    }

    @Test
    void execute_returns_success_when_no_files_match() throws Exception {
        String sourceDir = toFsPath(tempDir.toString());
        String archiveDir = toFsPath(tempDir.resolve("archives").toString());
        String config = String.format(
                "{\"sourceDir\":\"%s\",\"filePatterns\":[\"*.xyz\"],\"archiveDir\":\"%s\",\"archiveFormat\":\"ZIP\"}",
                sourceDir, archiveDir
        );
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = ExecutionContext.builder()
                .workingDir(sourceDir)
                .envVars(Map.of("JOB_NAME", "test"))
                .build();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
        assertTrue(result.logOutput().contains("No files matched"));
    }

    @Test
    void execute_matches_multiple_patterns() throws Exception {
        Files.writeString(tempDir.resolve("a.log"), "log1");
        Files.writeString(tempDir.resolve("b.log"), "log2");
        Files.writeString(tempDir.resolve("c.txt"), "text");

        String sourceDir = toFsPath(tempDir.toString());
        String archiveDir = toFsPath(tempDir.resolve("archives").toString());
        String config = String.format(
                "{\"sourceDir\":\"%s\",\"filePatterns\":[\"*.log\",\"*.txt\"],\"archiveDir\":\"%s\",\"archiveFormat\":\"ZIP\"}",
                sourceDir, archiveDir
        );
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = ExecutionContext.builder()
                .workingDir(sourceDir)
                .envVars(Map.of("JOB_NAME", "multi"))
                .build();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
        assertTrue(result.logOutput().contains("a.log"));
        assertTrue(result.logOutput().contains("b.log"));
        assertTrue(result.logOutput().contains("c.txt"));
    }

    @Test
    void execute_uses_default_job_name() throws Exception {
        Files.writeString(tempDir.resolve("file.txt"), "content");

        String sourceDir = toFsPath(tempDir.toString());
        String archiveDir = toFsPath(tempDir.resolve("archives").toString());
        String config = String.format(
                "{\"sourceDir\":\"%s\",\"filePatterns\":[\"*.txt\"],\"archiveDir\":\"%s\",\"archiveFormat\":\"ZIP\"}",
                sourceDir, archiveDir
        );
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = ExecutionContext.builder()
                .workingDir(sourceDir)
                .envVars(Map.of())
                .build();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
    }
}
