package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class EnvSetupStepExecutorTest {

    private final EnvSetupStepExecutor executor = new EnvSetupStepExecutor(new JsonParser());

    @TempDir
    Path tempDir;

    private String getJavaHomeRoot() {
        String realJavaHome = System.getProperty("java.home");
        String root;
        if (realJavaHome != null && realJavaHome.endsWith("jre")) {
            root = realJavaHome.substring(0, realJavaHome.length() - 3);
        } else {
            root = realJavaHome;
        }
        return root.replace("\\", "/");
    }

    private StepContext newContext() {
        return StepContext.builder()
                .envVars(new HashMap<>())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .build();
    }

    @Test
    void getType_returns_ENV_SETUP() {
        assertEquals("ENV_SETUP", executor.getType());
    }

    @Test
    void getConfigSchema_has_required_javaHome_field() {
        var schema = executor.getConfigSchema();
        assertNotNull(schema);
        assertEquals("ENV_SETUP", schema.stepType());
        assertTrue(schema.fields().stream().anyMatch(f -> f.name().equals("javaHome") && f.required()));
    }

    @Test
    void execute_returns_failure_when_config_is_null() throws Exception {
        StepContext ctx = newContext();
        var stepCtx = StepContext.builder()
                .envVars(new HashMap<>())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig("")
                .build();

        StepResult result = executor.execute(stepCtx);

        assertFalse(result.isSuccess());
        assertTrue(result.getLogOutput().contains("null or empty"));
    }

    @Test
    void execute_returns_failure_when_java_home_does_not_exist() throws Exception {
        String config = """
                {"javaHome":"/nonexistent/path","classpathEntries":[],"extraEnvVars":{}}
                """;
        var stepCtx = StepContext.builder()
                .envVars(new HashMap<>())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(stepCtx);

        assertFalse(result.isSuccess());
        assertTrue(result.getLogOutput().contains("does not exist"));
    }

    @Test
    void execute_validates_java_home_and_sets_context() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = String.format(
                "{\"javaHome\":\"%s\",\"classpathEntries\":[],\"extraEnvVars\":{\"MYVAR\":\"myval\"}}",
                javaHomeRoot
        );
        var stepCtx = StepContext.builder()
                .envVars(new HashMap<>())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(stepCtx);

        assertTrue(result.isSuccess());
        assertTrue(result.getLogOutput().contains("JAVA_HOME validated"));
        assertEquals(javaHomeRoot, stepCtx.getJavaHome());
        assertEquals("myval", stepCtx.getEnvVars().get("MYVAR"));
    }

    @Test
    void execute_logs_warning_for_missing_classpath_entries() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = String.format(
                "{\"javaHome\":\"%s\",\"classpathEntries\":[\"/fake/lib.jar\"],\"extraEnvVars\":{}}",
                javaHomeRoot
        );
        var stepCtx = StepContext.builder()
                .envVars(new HashMap<>())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(stepCtx);

        assertTrue(result.isSuccess());
        assertTrue(result.getLogOutput().contains("WARNING"));
        assertTrue(result.getLogOutput().contains("classpath entry not found"));
    }

    @Test
    void execute_sets_classpath_in_context() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = String.format(
                "{\"javaHome\":\"%s\",\"classpathEntries\":[\"lib/a.jar\",\"lib/b.jar\"],\"extraEnvVars\":{}}",
                javaHomeRoot
        );
        var stepCtx = StepContext.builder()
                .envVars(new HashMap<>())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(stepCtx);

        assertTrue(result.isSuccess());
        assertNotNull(stepCtx.getClasspath());
        assertEquals(2, stepCtx.getClasspath().size());
    }

    @Test
    void execute_handles_null_extra_env_vars() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = String.format(
                "{\"javaHome\":\"%s\",\"classpathEntries\":[],\"extraEnvVars\":null}",
                javaHomeRoot
        );
        var stepCtx = StepContext.builder()
                .envVars(new HashMap<>())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(stepCtx);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_with_temp_dir_as_java_home() throws Exception {
        Path fakeJavaHome = tempDir.resolve("jdk");
        Path binDir = fakeJavaHome.resolve("bin");
        Files.createDirectories(binDir);
        String exeName = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        Path javaExe = binDir.resolve(exeName);
        Files.createFile(javaExe);
        javaExe.toFile().setExecutable(true);

        String config = String.format(
                "{\"javaHome\":\"%s\",\"classpathEntries\":[],\"extraEnvVars\":{}}",
                fakeJavaHome.toString().replace("\\", "/")
        );
        var stepCtx = StepContext.builder()
                .envVars(new HashMap<>())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(stepCtx);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_merges_multiple_env_vars() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = String.format(
                "{\"javaHome\":\"%s\",\"classpathEntries\":[],\"extraEnvVars\":{\"A\":\"1\",\"B\":\"2\",\"C\":\"3\"}}",
                javaHomeRoot
        );
        var stepCtx = StepContext.builder()
                .envVars(new HashMap<>())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(stepCtx);

        assertTrue(result.isSuccess());
        assertEquals("1", stepCtx.getEnvVars().get("A"));
        assertEquals("2", stepCtx.getEnvVars().get("B"));
        assertEquals("3", stepCtx.getEnvVars().get("C"));
        assertTrue(result.getLogOutput().contains("Merged 3 extra env vars"));
    }
}
