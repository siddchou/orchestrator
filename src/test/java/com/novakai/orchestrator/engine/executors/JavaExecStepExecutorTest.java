package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

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
    void getType_returns_JAVA_EXEC() {
        assertEquals("JAVA_EXEC", executor.getType());
    }

    @Test
    void execute_returns_failure_when_config_is_null() throws Exception {
        var ctx = StepContext.builder()
                .javaHome(getJavaHomeRoot())
                .workDir(java.nio.file.Path.of(System.getProperty("user.dir").replace("\\", "/")))
                .envVars(new java.util.HashMap<>())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig("")
                .build();

        StepResult result = executor.execute(ctx);

        assertFalse(result.isSuccess());
        assertTrue(result.getLogOutput().contains("null or empty"));
    }

    @Test
    void execute_with_main_class_runs_successfully() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = """
                {"mainClass":"java.lang.Object","jarPath":null,"args":[],"jvmArgs":null,"timeoutMinutes":1}
                """;
        var ctx = StepContext.builder()
                .javaHome(javaHomeRoot)
                .classpath(List.of())
                .workDir(java.nio.file.Path.of(System.getProperty("user.dir").replace("\\", "/")))
                .envVars(new java.util.HashMap<>())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(ctx);

        assertNotNull(result);
        assertTrue(result.getLogOutput().contains("Executing:"));
    }

    @Test
    void execute_builds_command_with_jvm_args() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = """
                {"mainClass":"java.lang.Object","jarPath":null,"args":[],"jvmArgs":["-Xmx256m"],"timeoutMinutes":1}
                """;
        var ctx = StepContext.builder()
                .javaHome(javaHomeRoot)
                .classpath(List.of())
                .workDir(java.nio.file.Path.of(System.getProperty("user.dir").replace("\\", "/")))
                .envVars(new java.util.HashMap<>())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.getLogOutput().contains("-Xmx256m"));
    }

    @Test
    void execute_with_classpath_uses_platform_separator() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = """
                {"mainClass":"java.lang.Object","jarPath":null,"args":[],"jvmArgs":null,"timeoutMinutes":1}
                """;
        var ctx = StepContext.builder()
                .javaHome(javaHomeRoot)
                .classpath(List.of("lib/a.jar", "lib/b.jar"))
                .workDir(java.nio.file.Path.of(System.getProperty("user.dir").replace("\\", "/")))
                .envVars(new java.util.HashMap<>())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(ctx);

        assertTrue(result.getLogOutput().contains("-cp"));
        String expectedSep = System.getProperty("path.separator");
        String cpSection = result.getLogOutput()
                .substring(result.getLogOutput().indexOf("-cp"));
        assertTrue(cpSection.contains(expectedSep),
                "Classpath should use platform separator '" + expectedSep + "'");
    }

    @Test
    void execute_handles_null_jvm_args() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = """
                {"mainClass":"java.lang.Object","jarPath":null,"args":null,"jvmArgs":null,"timeoutMinutes":1}
                """;
        var ctx = StepContext.builder()
                .javaHome(javaHomeRoot)
                .classpath(List.of())
                .workDir(java.nio.file.Path.of(System.getProperty("user.dir").replace("\\", "/")))
                .envVars(new java.util.HashMap<>())
                .logSink(new StepContext.LogSink(new LinkedBlockingQueue<>()))
                .stepConfig(config)
                .build();

        StepResult result = executor.execute(ctx);

        assertNotNull(result);
    }

    @Test
    void execute_with_live_log_queue() throws Exception {
        String javaHomeRoot = getJavaHomeRoot();

        String config = """
                {"mainClass":"java.lang.Object","jarPath":null,"args":[],"jvmArgs":null,"timeoutMinutes":1}
                """;
        var queue = new LinkedBlockingQueue<String>();
        var ctx = StepContext.builder()
                .javaHome(javaHomeRoot)
                .classpath(List.of())
                .workDir(java.nio.file.Path.of(System.getProperty("user.dir").replace("\\", "/")))
                .envVars(new java.util.HashMap<>())
                .logSink(new StepContext.LogSink(queue))
                .stepConfig(config)
                .build();

        executor.execute(ctx);

        assertFalse(queue.isEmpty(), "Live log queue should have entries");
        assertTrue(queue.stream().anyMatch(s -> s.contains("Executing:")));
    }
}
