package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.domain.config.LogCleanupConfig;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.StepType;
import com.novakai.orchestrator.engine.ExecutionContext;
import com.novakai.orchestrator.engine.StepExecutor;
import com.novakai.orchestrator.engine.StepResult;
import com.novakai.orchestrator.engine.JsonParser;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.*;

@Component
@Slf4j
public class LogCleanupStepExecutor implements StepExecutor {

    private final JsonParser jsonParser;

    public LogCleanupStepExecutor(JsonParser jsonParser) {
        this.jsonParser = jsonParser;
    }

    @Override
    public StepType getSupportedType() {
        return StepType.LOG_CLEANUP;
    }

    @Override
    public StepResult execute(ExecutionContext ctx, JobStep step) throws Exception {
        StringBuilder output = new StringBuilder();
        LogCleanupConfig config = jsonParser.parse(step.getStepConfig(), LogCleanupConfig.class);

        if (config == null) {
            return StepResult.failure("LogCleanupConfig is null or empty");
        }

        Path dir = resolveDir(ctx.getWorkingDir(), config.directory());
        if (!Files.isDirectory(dir)) {
            return StepResult.failure("Cleanup directory does not exist: " + dir);
        }

        log.info("LogCleanup: scanning dir={} pattern={}", dir, config.filePattern());

        PathMatcher matcher = FileSystems.getDefault()
            .getPathMatcher("glob:" + config.filePattern());

        int deleted = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                boolean matches = matcher.matches(Path.of(name));
                log.info("LogCleanup: file={} matches={}", name, matches);
                if (matches) {
                    Files.delete(file);
                    output.append("Deleted: ").append(file.getFileName()).append("\n");
                    deleted++;
                }
            }
        }

        output.append("Total files deleted: ").append(deleted).append("\n");
        return StepResult.success(output.toString());
    }

    private Path resolveDir(String workingDir, String dir) {
        Path d = Path.of(dir);
        return d.isAbsolute() ? d : Path.of(workingDir).resolve(d);
    }
}
