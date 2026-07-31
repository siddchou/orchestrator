package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class ArchiveStepExecutorTest {

    private final ArchiveStepExecutor executor = new ArchiveStepExecutor(new JsonParser());

    @TempDir
    Path tempDir;

    private String toFsPath(String path) {
        return path.replace("\\", "/");
    }

    @Test
    void getType_returns_ARCHIVE() {
        assertEquals("ARCHIVE", executor.getType());
    }

    @Test
    void getConfigSchema_has_required_fields() {
        var schema = executor.getConfigSchema();
        assertNotNull(schema);
        assertEquals("ARCHIVE", schema.stepType());
        assertTrue(schema.fields().stream().anyMatch(f -> f.name().equals("sourceDir") && f.required()));
        assertTrue(schema.fields().stream().anyMatch(f -> f.name().equals("archiveFormat") && f.type().name().equals("ENUM")));
    }

    @Test
    void execute_returns_failure_when_config_is_null() throws Exception {
        var ctx = StepContext.builder()
                .workDir(Path.of(toFsPath(tempDir.toString())))
                .envVars(Map.of("JOB_NAME", "test"))
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig("")
                .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess());
        assertTrue(result.getLogOutput().contains("null or empty"));
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
        var ctx = StepContext.builder()
                .workDir(Path.of(sourceDir))
                .envVars(Map.of("JOB_NAME", "testjob"))
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.isSuccess());
        assertTrue(result.getLogOutput().contains("Archive created:"));
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
        var ctx = StepContext.builder()
                .workDir(Path.of(sourceDir))
                .envVars(Map.of("JOB_NAME", "csvjob"))
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.isSuccess());
        assertTrue(result.getLogOutput().contains("Archive created:"));
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
        var ctx = StepContext.builder()
                .workDir(Path.of(sourceDir))
                .envVars(Map.of("JOB_NAME", "test"))
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.isSuccess());
        assertTrue(result.getLogOutput().contains("No files matched"));
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
        var ctx = StepContext.builder()
                .workDir(Path.of(sourceDir))
                .envVars(Map.of("JOB_NAME", "multi"))
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.isSuccess());
        assertTrue(result.getLogOutput().contains("a.log"));
        assertTrue(result.getLogOutput().contains("b.log"));
        assertTrue(result.getLogOutput().contains("c.txt"));
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
        var ctx = StepContext.builder()
                .workDir(Path.of(sourceDir))
                .envVars(Map.of())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.isSuccess());
    }
}
