package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.domain.config.LogCleanupConfig;
import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.FieldType;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepExecutor;
import com.novakai.orchestrator.engine.spi.StepResult;
import org.springframework.stereotype.Component;

import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class LogCleanupStepExecutor implements StepExecutor {

    private final JsonParser jsonParser;

    public LogCleanupStepExecutor(JsonParser jsonParser) {
        this.jsonParser = jsonParser;
    }

    @Override
    public String getType() {
        return "LOG_CLEANUP";
    }

    @Override
    public StepConfigSchema getConfigSchema() {
        return new StepConfigSchema("LOG_CLEANUP", "Log Cleanup", List.of(
            new FieldDefinition("directory", "Directory to Clean", FieldType.STRING, true, null, null, "Absolute or relative path to the directory"),
            new FieldDefinition("filePattern", "File Pattern", FieldType.FILE_PATTERN, false, null, null, "Glob pattern for files to delete (e.g., *.log)"),
            new FieldDefinition("extraPatterns", "Extra Patterns", FieldType.LIST_STRING, false, null, null, "Additional comma-separated glob patterns to delete")
        ));
    }

    @Override
    public StepResult execute(StepContext ctx) throws Exception {
        long start = System.nanoTime();
        StringBuilder output = new StringBuilder();
        LogCleanupConfig config = jsonParser.parse(ctx.getStepConfig(), LogCleanupConfig.class);

        if (config == null) {
            return StepResult.failure("LogCleanupConfig is null or empty", Duration.ofNanos(System.nanoTime() - start));
        }

        Path dir = resolveDir(ctx.getWorkingDir(), config.directory());
        if (!Files.isDirectory(dir)) {
            return StepResult.failure("Cleanup directory does not exist: " + dir, Duration.ofNanos(System.nanoTime() - start));
        }

        List<String> patterns = new ArrayList<>();
        if (config.filePattern() != null && !config.filePattern().isEmpty()) {
            patterns.add(config.filePattern());
        }
        if (config.extraPatterns() != null) {
            patterns.addAll(config.extraPatterns());
        }

        if (patterns.isEmpty()) {
            return StepResult.failure("No file patterns specified", Duration.ofNanos(System.nanoTime() - start));
        }

        List<PathMatcher> matchers = patterns.stream()
            .map(p -> FileSystems.getDefault().getPathMatcher("glob:" + p))
            .toList();

        int deleted = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                boolean matches = matchers.stream().anyMatch(m -> m.matches(Path.of(name)));
                if (matches) {
                    Files.delete(file);
                    output.append("Deleted: ").append(file.getFileName()).append("\n");
                    deleted++;
                }
            }
        }

        output.append("Total files deleted: ").append(deleted).append("\n");
        return StepResult.success(java.util.Map.of(), output.toString(), Duration.ofNanos(System.nanoTime() - start));
    }

    private Path resolveDir(String workingDir, String dir) {
        Path d = Path.of(dir);
        return d.isAbsolute() ? d : Path.of(workingDir).resolve(d);
    }
}
