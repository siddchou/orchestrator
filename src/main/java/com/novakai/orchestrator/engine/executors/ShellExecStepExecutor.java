package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.FieldType;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepExecutor;
import com.novakai.orchestrator.engine.spi.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ShellExecStepExecutor implements StepExecutor {

    private final JsonParser jsonParser;

    @Override
    public String getType() {
        return "SHELL_EXEC";
    }

    @Override
    public StepConfigSchema getConfigSchema() {
        return new StepConfigSchema(
                "SHELL_EXEC",
                "Shell Command",
                List.of(
                        new FieldDefinition("command", "Command", FieldType.STRING, false, null, null, "Shell command to execute (one of command or scriptPath required)"),
                        new FieldDefinition("scriptPath", "Script Path", FieldType.FILE_PATTERN, false, null, null, "Path to shell script file (one of command or scriptPath required)"),
                        new FieldDefinition("args", "Arguments", FieldType.STRING, false, null, null, "Space-separated arguments"),
                        new FieldDefinition("workingDirectory", "Working Directory", FieldType.STRING, false, null, null, "Defaults to job workDir"),
                        new FieldDefinition("timeoutSeconds", "Timeout (seconds)", FieldType.NUMBER, false, 300, null, "Execution timeout in seconds"),
                        new FieldDefinition("envOverrides", "Environment Overrides", FieldType.STRING, false, null, null, "JSON map of env var name/value pairs")
                )
        );
    }

    @Override
    public StepResult execute(StepContext ctx) throws Exception {
        long startTime = System.nanoTime();

        if (ctx.getStepConfig() == null || ctx.getStepConfig().isBlank()) {
            return StepResult.failure("SHELL_EXEC config is null or empty", Duration.ofNanos(System.nanoTime() - startTime));
        }

        Map<String, Object> config = jsonParser.parse(ctx.getStepConfig(), Map.class);

        String command = (String) config.get("command");
        String scriptPath = (String) config.get("scriptPath");

        if ((command == null || command.isBlank()) && (scriptPath == null || scriptPath.isBlank())) {
            return StepResult.failure("SHELL_EXEC: one of 'command' or 'scriptPath' is required", Duration.ofNanos(System.nanoTime() - startTime));
        }

        int timeoutSeconds = config.containsKey("timeoutSeconds") ? Integer.parseInt(config.get("timeoutSeconds").toString()) : 300;

        // Build command list
        List<String> commandList = new ArrayList<>();
        if (scriptPath != null && !scriptPath.isBlank()) {
            commandList.add(scriptPath);
        } else {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            String shell = isWindows ? "cmd.exe" : "/bin/sh";
            String cmdArg = isWindows ? "/c" : "-c";
            commandList.add(shell);
            commandList.add(cmdArg);
            commandList.add(command);
        }

        // Append args
        Object argsObj = config.get("args");
        if (argsObj instanceof String argsStr && !argsStr.isBlank()) {
            for (String arg : argsStr.split("\\s+")) {
                commandList.add(arg);
            }
        }

        // Working directory
        Path workDir = ctx.getWorkDir();
        if (config.containsKey("workingDirectory")) {
            String wd = config.get("workingDirectory").toString();
            if (!wd.isBlank()) {
                workDir = Path.of(wd);
            }
        }

        // Environment overrides
        Map<String, String> envOverrides;
        if (config.containsKey("envOverrides") && config.get("envOverrides") instanceof Map<?, ?>) {
            envOverrides = new HashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) config.get("envOverrides")).entrySet()) {
                envOverrides.put(entry.getKey().toString(), String.valueOf(entry.getValue()));
            }
        } else {
            envOverrides = Map.of();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(commandList);
            if (workDir != null) {
                pb.directory(workDir.toFile());
            }

            // Merge environment overrides with inherited env
            Map<String, String> processEnv = pb.environment();
            for (Map.Entry<String, String> entry : envOverrides.entrySet()) {
                processEnv.put(entry.getKey(), entry.getValue());
            }

            pb.redirectErrorStream(true);

            ctx.getLogSink().log("Executing: " + commandList.get(0) + (commandList.size() > 1 ? " ..." : ""));

            Process process = pb.start();

            // Read output concurrently
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        ctx.getLogSink().log(line);
                    }
                } catch (Exception e) {
                    log.warn("Error reading process output", e);
                }
            });
            outputReader.start();

            boolean finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            outputReader.join(2000);

            if (!finished) {
                process.destroyForcibly();
                return StepResult.failure("SHELL_EXEC: command timed out after " + timeoutSeconds + "s", Duration.ofNanos(System.nanoTime() - startTime));
            }

            int exitCode = process.exitValue();

            if (exitCode != 0) {
                Map<String, Object> outputs = Map.of("exitCode", exitCode);
                return StepResult.failure(
                        "SHELL_EXEC: command exited with code " + exitCode,
                        Duration.ofNanos(System.nanoTime() - startTime));
            }

            ctx.getLogSink().log("Command completed successfully (exit code 0)");

            Map<String, Object> outputs = Map.of("exitCode", 0);
            return StepResult.success(outputs, "Shell command completed with exit code 0", Duration.ofNanos(System.nanoTime() - startTime));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StepResult.failure("SHELL_EXEC: interrupted", Duration.ofNanos(System.nanoTime() - startTime));
        } catch (Exception ex) {
            ctx.getLogSink().log("ERROR: " + ex.getMessage());
            return StepResult.failure("SHELL_EXEC failed: " + ex.getMessage(), Duration.ofNanos(System.nanoTime() - startTime));
        }
    }
}
