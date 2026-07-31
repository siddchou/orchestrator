package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.domain.config.EnvSetupConfig;
import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.PathUtils;
import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.FieldType;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepExecutor;
import com.novakai.orchestrator.engine.spi.StepResult;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class EnvSetupStepExecutor implements StepExecutor {

    private final JsonParser jsonParser;

    public EnvSetupStepExecutor(JsonParser jsonParser) {
        this.jsonParser = jsonParser;
    }

    @Override
    public String getType() {
        return "ENV_SETUP";
    }

    @Override
    public StepConfigSchema getConfigSchema() {
        return new StepConfigSchema("ENV_SETUP", "Environment Setup", List.of(
            new FieldDefinition("javaHome", "Java Home Directory", FieldType.STRING, true, null, null, "Path to the JDK installation"),
            new FieldDefinition("classpathEntries", "Classpath Entries", FieldType.LIST_STRING, false, null, null, "Comma-separated list of JAR paths or directories for the classpath"),
            new FieldDefinition("extraEnvVars", "Extra Environment Variables", FieldType.STRING, false, null, null, "JSON map of additional environment variables to merge")
        ));
    }

    @Override
    public StepResult execute(StepContext ctx) throws Exception {
        long start = System.nanoTime();
        StringBuilder output = new StringBuilder();
        EnvSetupConfig config = jsonParser.parse(ctx.getStepConfig(), EnvSetupConfig.class);

        if (config == null) {
            return StepResult.failure("EnvSetupConfig is null or empty", Duration.ofNanos(System.nanoTime() - start));
        }

        Path javaHome = Path.of(config.javaHome());
        if (!Files.isDirectory(javaHome)) {
            return StepResult.failure("JAVA_HOME does not exist: " + javaHome, Duration.ofNanos(System.nanoTime() - start));
        }
        Path javaBin = PathUtils.resolveJavaBinary(config.javaHome());
        if (!Files.isExecutable(javaBin)) {
            return StepResult.failure("java binary not executable: " + javaBin, Duration.ofNanos(System.nanoTime() - start));
        }
        output.append("JAVA_HOME validated: ").append(javaHome).append("\n");

        ctx.setJavaHome(config.javaHome());
        ctx.setClasspath(new ArrayList<>(config.classpathEntries()));

        for (String entry : config.classpathEntries()) {
            if (!Files.exists(Path.of(entry))) {
                output.append("WARNING: classpath entry not found: ").append(entry).append("\n");
            }
        }

        if (config.extraEnvVars() != null) {
            ctx.getEnvVars().putAll(config.extraEnvVars());
            output.append("Merged ").append(config.extraEnvVars().size()).append(" extra env vars\n");
        }

        return StepResult.success(java.util.Map.of(), output.toString(), Duration.ofNanos(System.nanoTime() - start));
    }
}
