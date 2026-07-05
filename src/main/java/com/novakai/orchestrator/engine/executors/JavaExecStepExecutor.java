package com.novakai.orchestrator.engine.executors;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.config.JavaExecConfig;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.StepType;
import com.novakai.orchestrator.engine.ExecutionContext;
import com.novakai.orchestrator.engine.StepExecutor;
import com.novakai.orchestrator.engine.StepResult;
import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.PathUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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

    public JavaExecStepExecutor(JsonParser jsonParser) {
        this.jsonParser = jsonParser;
    }

    @Override
    public StepType getSupportedType() {
        return StepType.JAVA_EXEC;
    }

    @Override
    public StepResult execute(ExecutionContext ctx, JobStep step) throws Exception {
        JavaExecConfig config = jsonParser.parse(step.getStepConfig(), JavaExecConfig.class);

        if (config == null) {
            return StepResult.failure("JavaExecConfig is null or empty");
        }

        // Validate inputs before building command
        String validationError = validateConfig(config);
        if (validationError != null) {
            return StepResult.failure(validationError);
        }

        StringBuilder output = new StringBuilder();

        String javaBin = PathUtils.resolveJavaBinary(ctx.getJavaHome()).toString();
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        if (config.jvmArgs() != null) {
            for (String arg : config.jvmArgs()) {
                if (!isSafeJvmArg(arg)) {
                    return StepResult.failure("Invalid JVM argument detected");
                }
                command.add(arg);
            }
        }

        if (config.jarPath() != null && !config.jarPath().isBlank()) {
            // Validate and resolve the jar path
            Path resolvedJarPath = validateAndResolveJarPath(config.jarPath(), ctx.getWorkingDir());
            if (resolvedJarPath == null) {
                return StepResult.failure("Invalid or unsafe jar path");
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
        int timeout = config.timeoutMinutes() != null ? config.timeoutMinutes() : defaultTimeoutMinutes;

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

        boolean completed = process.waitFor(timeout, TimeUnit.MINUTES);
        if (!completed) {
            process.destroyForcibly();
            return StepResult.failure(-1, output + "\nPROCESS TIMED OUT after " + timeout + " minutes");
        }

        int exitCode = process.exitValue();
        log.debug("JavaExec: process exited with code {}", exitCode);
        output.append("\nProcess exited with code: ").append(exitCode);

        return exitCode == 0
            ? StepResult.success(output.toString())
            : StepResult.failure(exitCode, output.toString());
    }

    /**
     * Validates the JavaExecConfig to prevent command injection.
     * Returns null if valid, or an error message if invalid.
     */
    private String validateConfig(JavaExecConfig config) {
        // Validate mainClass - must be a valid Java class name
        if (config.mainClass() != null && !config.mainClass().isBlank()) {
            if (!JAVA_CLASS_NAME_PATTERN.matcher(config.mainClass()).matches()) {
                return "Invalid mainClass: must be a valid Java fully-qualified class name";
            }
        }

        // Validate jarPath - must only contain safe characters
        if (config.jarPath() != null && !config.jarPath().isBlank()) {
            if (!SAFE_JAR_PATH_PATTERN.matcher(config.jarPath()).matches()) {
                return "Invalid jarPath: contains unsafe characters";
            }
        }

        // Validate jvmArgs - each arg must be safe
        if (config.jvmArgs() != null) {
            for (String arg : config.jvmArgs()) {
                if (!isSafeJvmArg(arg)) {
                    return "Invalid JVM argument detected";
                }
            }
        }

        return null;
    }

    /**
     * Checks if a JVM argument is safe (no shell metacharacters or command injection attempts).
     */
    private boolean isSafeJvmArg(String arg) {
        if (arg == null || arg.isBlank()) {
            return false;
        }
        // Only allow alphanumeric, dashes, underscores, dots, equals, colons, slashes
        // This prevents: ; | & $ ` () {} [] \n \r etc.
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (!Character.isLetterOrDigit(c) &&
                c != '-' && c != '_' && c != '.' && c != '=' && c != ':' && c != '/' && c != '@') {
                return false;
            }
        }
        // Block known dangerous patterns
        String lowerArg = arg.toLowerCase();
        return !lowerArg.contains("exec(") &&
               !lowerArg.contains("/bin/") &&
               !lowerArg.contains("cmd.exe") &&
               !arg.contains("$( ") &&
               !arg.contains("`");
    }

    /**
     * Validates and resolves the jar path to prevent directory traversal.
     * Returns null if the path is invalid or unsafe.
     */
    private Path validateAndResolveJarPath(String jarPath, String workingDir) {
        try {
            Path baseDir = Paths.get(workingDir).normalize();
            Path resolvedPath = baseDir.resolve(jarPath).normalize();

            // Ensure the resolved path is within the working directory (no traversal outside)
            if (!resolvedPath.startsWith(baseDir.toString())) {
                return null;
            }

            // Verify the file exists
            if (!Files.exists(resolvedPath)) {
                return null;
            }

            // Verify it's a regular file
            if (!Files.isRegularFile(resolvedPath)) {
                return null;
            }

            return resolvedPath;
        } catch (Exception e) {
            log.warn("Failed to validate jar path: {}", jarPath, e);
            return null;
        }
    }
}
