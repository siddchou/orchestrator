package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.domain.config.ArchiveConfig;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.StepType;
import com.novakai.orchestrator.engine.ExecutionContext;
import com.novakai.orchestrator.engine.StepExecutor;
import com.novakai.orchestrator.engine.StepResult;
import com.novakai.orchestrator.engine.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@Slf4j
public class ArchiveStepExecutor implements StepExecutor {

    private final JsonParser jsonParser;

    public ArchiveStepExecutor(JsonParser jsonParser) {
        this.jsonParser = jsonParser;
    }

    @Override
    public StepType getSupportedType() {
        return StepType.ARCHIVE;
    }

    @Override
    public StepResult execute(ExecutionContext ctx, JobStep step) throws Exception {
        ArchiveConfig config = jsonParser.parse(step.getStepConfig(), ArchiveConfig.class);

        if (config == null) {
            return StepResult.failure("ArchiveConfig is null or empty");
        }

        StringBuilder output = new StringBuilder();

        Path sourceDir = Path.of(config.sourceDir());
        List<Path> filesToArchive = new ArrayList<>();
        for (String pattern : config.filePatterns()) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir)) {
                for (Path file : stream) {
                    if (matcher.matches(file.getFileName())) {
                        filesToArchive.add(file);
                    }
                }
            }
        }

        if (filesToArchive.isEmpty()) {
            log.debug("Archive: no files matched patterns in {}", config.sourceDir());
            output.append("No files matched patterns for archiving\n");
            return StepResult.success(output.toString());
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String jobName = ctx.getEnvVars().getOrDefault("JOB_NAME", "job");
        String ext = "TAR_GZ".equals(config.archiveFormat()) ? ".tar.gz" : ".zip";
        Path archiveDir = Path.of(config.archiveDir());
        Files.createDirectories(archiveDir);
        Path archivePath = archiveDir.resolve(jobName + "_" + timestamp + ext);

        log.debug("Archive: creating {} with {} files", archivePath, filesToArchive.size());
        if ("TAR_GZ".equals(config.archiveFormat())) {
            writeTarGz(archivePath, filesToArchive, output);
        } else {
            writeZip(archivePath, filesToArchive, output);
        }

        output.append("Archive created: ").append(archivePath).append("\n");

        if (config.deleteOriginal()) {
            for (Path file : filesToArchive) {
                try {
                    Files.delete(file);
                    output.append("Deleted: ").append(file.getFileName()).append("\n");
                } catch (IOException ex) {
                    output.append("Failed to delete: ").append(file.getFileName())
                            .append(" - ").append(ex.getMessage()).append("\n");
                }
            }
        }

        return StepResult.success(output.toString());
    }

    private void writeZip(Path archivePath, List<Path> files, StringBuilder output) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archivePath))) {
            for (Path file : files) {
                zos.putNextEntry(new ZipEntry(file.getFileName().toString()));
                Files.copy(file, zos);
                zos.closeEntry();
                output.append("Archived: ").append(file.getFileName()).append("\n");
            }
        }
    }

    private void writeTarGz(Path archivePath, List<Path> files, StringBuilder output) throws IOException {
        try (OutputStream fos = Files.newOutputStream(archivePath);
             GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(fos);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            for (Path file : files) {
                TarArchiveEntry entry = new TarArchiveEntry(file.toFile(), file.getFileName().toString());
                tar.putArchiveEntry(entry);
                Files.copy(file, tar);
                tar.closeArchiveEntry();
                output.append("Archived: ").append(file.getFileName()).append("\n");
            }
        }
    }
}
