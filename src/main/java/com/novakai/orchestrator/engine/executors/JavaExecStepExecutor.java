package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.domain.config.JavaExecConfig;
import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.PathUtils;
import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.FieldType;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepExecutor;
import com.novakai.orchestrator.engine.spi.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
public class JavaExecStepExecutor implements StepExecutor {

    private final JsonParser jsonParser;

    @Value("${orchestrator.engine.default-step-timeout-minutes:60}")
    private int defaultTimeoutMinutes;

    // Regex patterns for input validation
    private static final Pattern JAVA_CLASS_NAME_PATTERN =
        Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$");
    private static final Pattern SAFE_JAR_PATH_PATTERN =
        Pattern.compile("^[a-zA-Z0-9_./\\\\-]+$");

    // Track running processes for graceful shutdown
    private final List<RunningProcess> runningProcesses = new CopyOnWriteArrayList<>();

    public JavaExecStepExecutor(JsonParser jsonParser) {
        this.jsonParser = jsonParser;
    }

    @Override
    public String getType() {
        return "JAVA_EXEC";
    }

    @Override
    public StepConfigSchema getConfigSchema() {
        return new StepConfigSchema("JAVA_EXEC", "Java Execution", List.of(
            new FieldDefinition("mainClass", "Main Class", FieldType.STRING, false, null, null, "Fully-qualified Java class name to execute"),
            new FieldDefinition("jarPath", "JAR Path", FieldType.FILE_PATTERN, false, null, null, "Path to JAR file (alternative to mainClass)"),
            new FieldDefinition("args", "Arguments", FieldType.STRING, false, null, null, "JSON array of application arguments"),
            new FieldDefinition("jvmArgs", "JVM Arguments", FieldType.STRING, false, null, null, "JSON array of JVM flags (e.g., -Xmx256m)"),
            new FieldDefinition("timeoutMinutes", "Timeout (minutes)", FieldType.NUMBER, false, null, null, "Execution timeout in minutes")
        ));
    }

    @Override
    public StepResult execute(StepContext ctx) throws Exception {
        long start = System.nanoTime();
        JavaExecConfig config = jsonParser.parse(ctx.getStepConfig(), JavaExecConfig.class);

        if (config == null) {
            return StepResult.failure("JavaExecConfig is null or empty", Duration.ofNanos(System.nanoTime() - start));
        }

        // Validate inputs before building command
        String validationError = validateConfig(config);
        if (validationError != null) {
            return StepResult.failure(validationError, Duration.ofNanos(System.nanoTime() - start));
        }

        StringBuilder output = new StringBuilder();

        String javaBin = PathUtils.resolveJavaBinary(ctx.getJavaHome()).toString();
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        if (config.jvmArgs() != null) {
            for (String arg : config.jvmArgs()) {
                if (!isSafeJvmArg(arg)) {
                    return StepResult.failure("Invalid JVM argument detected", Duration.ofNanos(System.nanoTime() - start));
                }
                command.add(arg);
            }
        }

        if (config.jarPath() != null && !config.jarPath().isBlank()) {
            Path resolvedJarPath = validateAndResolveJarPath(config.jarPath(), ctx.getWorkingDir());
            if (resolvedJarPath == null) {
                return StepResult.failure("Invalid or unsafe jar path", Duration.ofNanos(System.nanoTime() - start));
            }
            command.add("-jar");
            command.add(resolvedJarPath.toString());
        } else {
            if (ctx.getClasspath() != null && !ctx.getClasspath().isEmpty()) {
                command.add("-cp");
                command.add(PathUtils.joinClasspath(ctx.getClasspath()));
            }
            command.add(config.mainClass());
        }

        if (config.args() != null) {
            command.addAll(config.args());
        }

        output.append("Executing: ").append(String.join(" ", command)).append("\n");
        if (ctx.getLiveLogQueue() != null) {
            ctx.getLiveLogQueue().add("Executing: " + String.join(" ", command));
        }
        log.debug("JavaExec: command={}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(ctx.getWorkingDir()));
        pb.environment().putAll(ctx.getEnvVars());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        long pid = getProcessId(process);
        RunningProcess runningProcess = new RunningProcess(pid, process);
        if (pid > 0) {
            runningProcesses.add(runningProcess);
        }

        int timeout = config.timeoutMinutes() != null ? config.timeoutMinutes() : defaultTimeoutMinutes;
        boolean completed = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                if (ctx.getLiveLogQueue() != null) {
                    ctx.getLiveLogQueue().add(line);
                }
            }
        }

        boolean cancelled = false;
        try {
            completed = process.waitFor(timeout, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            log.warn("JavaExec process execution interrupted for PID {}", pid);
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            cancelled = true;
        }

        if (!completed && !cancelled) {
            process.destroyForcibly();
            return StepResult.failure(output + "\nPROCESS TIMED OUT after " + timeout + " minutes",
                Duration.ofNanos(System.nanoTime() - start));
        }

        int exitCode = process.exitValue();
        log.debug("JavaExec: process exited with code {}", exitCode);
        output.append("\nProcess exited with code: ").append(exitCode);

        runningProcesses.remove(runningProcess);

        Duration execTime = Duration.ofNanos(System.nanoTime() - start);
        if (exitCode == 0) {
            return StepResult.success(Map.of("exitCode", 0), output.toString(), execTime);
        } else {
            return new StepResult(
                com.novakai.orchestrator.engine.spi.StepStatus.FAILED,
                Map.of("exitCode", exitCode),
                output.toString(),
                execTime
            );
        }
    }

    private String validateConfig(JavaExecConfig config) {
        if (config.mainClass() != null && !config.mainClass().isBlank()) {
            if (!JAVA_CLASS_NAME_PATTERN.matcher(config.mainClass()).matches()) {
                return "Invalid mainClass: must be a valid Java fully-qualified class name";
            }
        }

        if (config.jarPath() != null && !config.jarPath().isBlank()) {
            if (!SAFE_JAR_PATH_PATTERN.matcher(config.jarPath()).matches()) {
                return "Invalid jarPath: contains unsafe characters";
            }
        }

        if (config.jvmArgs() != null) {
            for (String arg : config.jvmArgs()) {
                if (!isSafeJvmArg(arg)) {
                    return "Invalid JVM argument detected";
                }
            }
        }

        return null;
    }

    private boolean isSafeJvmArg(String arg) {
        if (arg == null || arg.isBlank()) {
            return false;
        }
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (!Character.isLetterOrDigit(c) &&
                c != '-' && c != '_' && c != '.' && c != '=' && c != ':' && c != '/' && c != '@') {
                return false;
            }
        }
        String lowerArg = arg.toLowerCase();
        return !lowerArg.contains("exec(") &&
               !lowerArg.contains("/bin/") &&
               !lowerArg.contains("cmd.exe") &&
               !arg.contains("$( ") &&
               !arg.contains("`");
    }

    private Path validateAndResolveJarPath(String jarPath, String workingDir) {
        try {
            Path baseDir = Paths.get(workingDir).normalize();
            Path resolvedPath = baseDir.resolve(jarPath).normalize();

            if (!resolvedPath.startsWith(baseDir.toString())) {
                return null;
            }

            if (!Files.exists(resolvedPath)) {
                return null;
            }

            if (!Files.isRegularFile(resolvedPath)) {
                return null;
            }

            return resolvedPath;
        } catch (Exception e) {
            log.warn("Failed to validate jar path: {}", jarPath, e);
            return null;
        }
    }

    private long getProcessId(Process process) {
        try {
            return process.toHandle().pid();
        } catch (UnsupportedOperationException | SecurityException e) {
            log.debug("Could not obtain PID from Process", e);
            return -1;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (!runningProcesses.isEmpty()) {
            log.info("Terminating {} running JavaExec processes on shutdown", runningProcesses.size());
            for (RunningProcess p : runningProcesses) {
                try {
                    p.process.destroyForcibly();
                    log.debug("Forcefully terminated process PID {}", p.pid);
                } catch (Exception e) {
                    log.warn("Failed to terminate process PID {}: {}", p.pid, e.getMessage());
                }
            }
        }
    }

    private static class RunningProcess {
        final long pid;
        final Process process;

        RunningProcess(long pid, Process process) {
            this.pid = pid;
            this.process = process;
        }
    }
}
