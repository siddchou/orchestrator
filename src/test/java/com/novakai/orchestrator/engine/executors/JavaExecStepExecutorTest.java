package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.StepType;
import com.novakai.orchestrator.engine.ExecutionContext;
import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.StepResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class JavaExecStepExecutorTest {

    private final JavaExecStepExecutor executor = new JavaExecStepExecutor(new JsonParser());

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

    @Test
    void getSupportedType_returns_JAVA_EXEC() {
        assertEquals(StepType.JAVA_EXEC, executor.getSupportedType());
    }

    @Test
    void execute_returns_failure_when_config_is_null() throws Exception {
        JobStep step = JobStep.builder().stepConfig("").build();
        ExecutionContext ctx = ExecutionContext.builder()
                .javaHome(getJavaHomeRoot())
                .workingDir(System.getProperty("user.dir").replace("\\", "/"))
                .envVars(new HashMap<>())
                .build();

        StepResult result = executor.execute(ctx, step);

        assertFalse(result.success());
        assertTrue(result.logOutput().contains("null or empty"));
    }

    @Test
    void execute_with_main_class_runs_successfully() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = """
                {"mainClass":"java.lang.Object","jarPath":null,"args":[],"jvmArgs":null,"timeoutMinutes":1}
                """;
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = ExecutionContext.builder()
                .javaHome(javaHomeRoot)
                .classpath(List.of())
                .workingDir(System.getProperty("user.dir").replace("\\", "/"))
                .envVars(new HashMap<>())
                .build();

        StepResult result = executor.execute(ctx, step);

        assertNotNull(result);
        assertTrue(result.logOutput().contains("Executing:"));
    }

    @Test
    void execute_builds_command_with_jvm_args() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = """
                {"mainClass":"java.lang.Object","jarPath":null,"args":[],"jvmArgs":["-Xmx256m"],"timeoutMinutes":1}
                """;
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = ExecutionContext.builder()
                .javaHome(javaHomeRoot)
                .classpath(List.of())
                .workingDir(System.getProperty("user.dir").replace("\\", "/"))
                .envVars(new HashMap<>())
                .build();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.logOutput().contains("-Xmx256m"));
    }

    @Test
    void execute_with_classpath_uses_platform_separator() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = """
                {"mainClass":"java.lang.Object","jarPath":null,"args":[],"jvmArgs":null,"timeoutMinutes":1}
                """;
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = ExecutionContext.builder()
                .javaHome(javaHomeRoot)
                .classpath(List.of("lib/a.jar", "lib/b.jar"))
                .workingDir(System.getProperty("user.dir").replace("\\", "/"))
                .envVars(new HashMap<>())
                .build();

        StepResult result = executor.execute(ctx, step);

        assertTrue(result.logOutput().contains("-cp"));
        String expectedSep = System.getProperty("path.separator");
        String cpSection = result.logOutput()
                .substring(result.logOutput().indexOf("-cp"));
        assertTrue(cpSection.contains(expectedSep),
                "Classpath should use platform separator '" + expectedSep + "'");
    }

    @Test
    void execute_handles_null_jvm_args() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = """
                {"mainClass":"java.lang.Object","jarPath":null,"args":null,"jvmArgs":null,"timeoutMinutes":1}
                """;
        JobStep step = JobStep.builder().stepConfig(config).build();
        ExecutionContext ctx = ExecutionContext.builder()
                .javaHome(javaHomeRoot)
                .classpath(List.of())
                .workingDir(System.getProperty("user.dir").replace("\\", "/"))
                .envVars(new HashMap<>())
                .build();

        StepResult result = executor.execute(ctx, step);

        assertNotNull(result);
    }

    @Test
    void execute_with_live_log_queue() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = """
                {"mainClass":"java.lang.Object","jarPath":null,"args":[],"jvmArgs":null,"timeoutMinutes":1}
                """;
        JobStep step = JobStep.builder().stepConfig(config).build();
        var queue = new java.util.concurrent.LinkedBlockingQueue<String>();
        ExecutionContext ctx = ExecutionContext.builder()
                .javaHome(javaHomeRoot)
                .classpath(List.of())
                .workingDir(System.getProperty("user.dir").replace("\\", "/"))
                .envVars(new HashMap<>())
                .liveLogQueue(queue)
                .build();

        executor.execute(ctx, step);

        assertFalse(queue.isEmpty(), "Live log queue should have entries");
        assertTrue(queue.stream().anyMatch(s -> s.contains("Executing:")));
    }
}
