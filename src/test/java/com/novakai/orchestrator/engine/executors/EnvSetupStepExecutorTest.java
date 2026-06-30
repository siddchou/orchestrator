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
import java.util.HashMap;
import java.util.Map;

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

    private ExecutionContext newContext() {
        return ExecutionContext.builder()
                .envVars(new HashMap<>())
                .build();
    }

    @Test
    void getSupportedType_returns_ENV_SETUP() {
        assertEquals(StepType.ENV_SETUP, executor.getSupportedType());
    }

    @Test
    void execute_returns_failure_when_config_is_null() throws Exception {
        JobStep step = JobStep.builder().stepConfig("").build();
        ExecutionContext ctx = newContext();

        StepResult result = executor.execute(ctx, step);

        assertFalse(result.success());
        assertTrue(result.logOutput().contains("null or empty"));
    }

    @Test
    void execute_returns_failure_when_java_home_does_not_exist() throws Exception {
        String config = """
                {"javaHome":"/nonexistent/path","classpathEntries":[],"extraEnvVars":{}}
                """;
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = newContext();

        StepResult result = executor.execute(ctx, step);

        assertFalse(result.success());
        assertTrue(result.logOutput().contains("does not exist"));
    }

    @Test
    void execute_validates_java_home_and_sets_context() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = String.format(
                "{\"javaHome\":\"%s\",\"classpathEntries\":[],\"extraEnvVars\":{\"MYVAR\":\"myval\"}}",
                javaHomeRoot
        );
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = newContext();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
        assertTrue(result.logOutput().contains("JAVA_HOME validated"));
        assertEquals(javaHomeRoot, ctx.getJavaHome());
        assertEquals("myval", ctx.getEnvVars().get("MYVAR"));
    }

    @Test
    void execute_logs_warning_for_missing_classpath_entries() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = String.format(
                "{\"javaHome\":\"%s\",\"classpathEntries\":[\"/fake/lib.jar\"],\"extraEnvVars\":{}}",
                javaHomeRoot
        );
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = newContext();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
        assertTrue(result.logOutput().contains("WARNING"));
        assertTrue(result.logOutput().contains("classpath entry not found"));
    }

    @Test
    void execute_sets_classpath_in_context() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = String.format(
                "{\"javaHome\":\"%s\",\"classpathEntries\":[\"lib/a.jar\",\"lib/b.jar\"],\"extraEnvVars\":{}}",
                javaHomeRoot
        );
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = newContext();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
        assertNotNull(ctx.getClasspath());
        assertEquals(2, ctx.getClasspath().size());
    }

    @Test
    void execute_handles_null_extra_env_vars() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = String.format(
                "{\"javaHome\":\"%s\",\"classpathEntries\":[],\"extraEnvVars\":null}",
                javaHomeRoot
        );
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = newContext();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
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
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = newContext();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
    }

    @Test
    void execute_merges_multiple_env_vars() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = String.format(
                "{\"javaHome\":\"%s\",\"classpathEntries\":[],\"extraEnvVars\":{\"A\":\"1\",\"B\":\"2\",\"C\":\"3\"}}",
                javaHomeRoot
        );
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = newContext();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.success());
        assertEquals("1", ctx.getEnvVars().get("A"));
        assertEquals("2", ctx.getEnvVars().get("B"));
        assertEquals("3", ctx.getEnvVars().get("C"));
        assertTrue(result.logOutput().contains("Merged 3 extra env vars"));
    }
}
