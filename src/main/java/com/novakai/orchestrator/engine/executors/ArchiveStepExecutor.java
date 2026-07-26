package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.domain.config.ArchiveConfig;
import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.FieldType;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepExecutor;
import com.novakai.orchestrator.engine.spi.StepResult;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class ArchiveStepExecutor implements StepExecutor {

    private final JsonParser jsonParser;

    public ArchiveStepExecutor(JsonParser jsonParser) {
        this.jsonParser = jsonParser;
    }

    @Override
    public String getType() {
        return "ARCHIVE";
    }

    @Override
    public StepConfigSchema getConfigSchema() {
        return new StepConfigSchema("ARCHIVE", "Archive Files", List.of(
            new FieldDefinition("sourceDir", "Source Directory", FieldType.STRING, true, null, null, "Directory containing files to archive"),
            new FieldDefinition("filePatterns", "File Patterns", FieldType.STRING, true, null, null, "JSON array of glob patterns for files to include"),
            new FieldDefinition("archiveDir", "Archive Output Directory", FieldType.STRING, true, null, null, "Directory where the archive file will be created"),
            new FieldDefinition("archiveFormat", "Archive Format", FieldType.ENUM, false, "ZIP", List.of("TAR_GZ", "ZIP"), "Output format: TAR_GZ or ZIP"),
            new FieldDefinition("deleteOriginal", "Delete Original Files", FieldType.BOOLEAN, false, false, null, "Whether to delete source files after archiving")
        ));
    }

    @Override
    public StepResult execute(StepContext ctx) throws Exception {
        long start = System.nanoTime();
        ArchiveConfig config = jsonParser.parse(ctx.getStepConfig(), ArchiveConfig.class);

        if (config == null) {
            return StepResult.failure("ArchiveConfig is null or empty", Duration.ofNanos(System.nanoTime() - start));
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
            output.append("No files matched patterns for archiving\n");
            return StepResult.success(java.util.Map.of(), output.toString(), Duration.ofNanos(System.nanoTime() - start));
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String jobName = ctx.getEnvVars().getOrDefault("JOB_NAME", "job");
        String ext = "TAR_GZ".equals(config.archiveFormat()) ? ".tar.gz" : ".zip";
        Path archiveDir = Path.of(config.archiveDir());
        Files.createDirectories(archiveDir);
        Path archivePath = archiveDir.resolve(jobName + "_" + timestamp + ext);

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

        return StepResult.success(java.util.Map.of(), output.toString(), Duration.ofNanos(System.nanoTime() - start));
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
