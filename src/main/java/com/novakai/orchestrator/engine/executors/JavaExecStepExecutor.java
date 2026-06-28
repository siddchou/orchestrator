package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.domain.config.JavaExecConfig;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.StepType;
import com.novakai.orchestrator.engine.ExecutionContext;
import com.novakai.orchestrator.engine.StepExecutor;
import com.novakai.orchestrator.engine.StepResult;
import com.novakai.orchestrator.engine.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class JavaExecStepExecutor implements StepExecutor {

    private final JsonParser jsonParser;

    @Value("${orchestrator.engine.default-step-timeout-minutes:60}")
    private int defaultTimeoutMinutes;

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

        StringBuilder log = new StringBuilder();

        String javaBin = ctx.getJavaHome() + "/bin/java";
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        if (config.jvmArgs() != null) {
            command.addAll(config.jvmArgs());
        }

        if (config.jarPath() != null && !config.jarPath().isBlank()) {
            command.add("-jar");
            command.add(config.jarPath());
        } else {
            if (ctx.getClasspath() != null && !ctx.getClasspath().isEmpty()) {
                command.add("-cp");
                command.add(String.join(":", ctx.getClasspath()));
            }
            command.add(config.mainClass());
        }

        if (config.args() != null) {
            command.addAll(config.args());
        }

        log.append("Executing: ").append(String.join(" ", command)).append("\n");
        if (ctx.getLiveLogQueue() != null) {
            ctx.getLiveLogQueue().add("Executing: " + String.join(" ", command));
        }

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
                log.append(line).append("\n");
                if (ctx.getLiveLogQueue() != null) {
                    ctx.getLiveLogQueue().add(line);
                }
            }
        }

        boolean completed = process.waitFor(timeout, TimeUnit.MINUTES);
        if (!completed) {
            process.destroyForcibly();
            return StepResult.failure(-1, log + "\nPROCESS TIMED OUT after " + timeout + " minutes");
        }

        int exitCode = process.exitValue();
        log.append("\nProcess exited with code: ").append(exitCode);

        return exitCode == 0
            ? StepResult.success(log.toString())
            : StepResult.failure(exitCode, log.toString());
    }
}
