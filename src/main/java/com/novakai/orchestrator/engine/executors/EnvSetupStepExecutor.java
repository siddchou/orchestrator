package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.domain.config.EnvSetupConfig;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.StepType;
import com.novakai.orchestrator.engine.ExecutionContext;
import com.novakai.orchestrator.engine.StepExecutor;
import com.novakai.orchestrator.engine.StepResult;
import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.PathUtils;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

@Component
@Slf4j
public class EnvSetupStepExecutor implements StepExecutor {

    private final JsonParser jsonParser;

    public EnvSetupStepExecutor(JsonParser jsonParser) {
        this.jsonParser = jsonParser;
    }

    @Override
    public StepType getSupportedType() {
        return StepType.ENV_SETUP;
    }

    @Override
    public StepResult execute(ExecutionContext ctx, JobStep step) throws Exception {
        StringBuilder output = new StringBuilder();
        EnvSetupConfig config = jsonParser.parse(step.getStepConfig(), EnvSetupConfig.class);

        if (config == null) {
            return StepResult.failure("EnvSetupConfig is null or empty");
        }

        Path javaHome = Path.of(config.javaHome());
        if (!Files.isDirectory(javaHome)) {
            return StepResult.failure("JAVA_HOME does not exist: " + javaHome);
        }
        Path javaBin = PathUtils.resolveJavaBinary(config.javaHome());
        if (!Files.isExecutable(javaBin)) {
            return StepResult.failure("java binary not executable: " + javaBin);
        }
        output.append("JAVA_HOME validated: ").append(javaHome).append("\n");

        log.debug("EnvSetup: JAVA_HOME={}, classpath entries={}", javaHome, config.classpathEntries().size());
        ctx.setJavaHome(config.javaHome());
        ctx.setClasspath(new ArrayList<>(config.classpathEntries()));

        for (String entry : config.classpathEntries()) {
            if (!Files.exists(Path.of(entry))) {
                output.append("WARNING: classpath entry not found: ").append(entry).append("\n");
            }
        }

        if (config.extraEnvVars() != null) {
            ctx.getEnvVars().putAll(config.extraEnvVars());
            log.debug("EnvSetup: merged {} extra env vars", config.extraEnvVars().size());
            output.append("Merged ").append(config.extraEnvVars().size()).append(" extra env vars\n");
        }

        return StepResult.success(output.toString());
    }
}
