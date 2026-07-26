package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.domain.config.SftpConfig;
import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.FieldType;
import com.novakai.orchestrator.engine.spi.StepConfigSchema;
import com.novakai.orchestrator.engine.spi.StepContext;
import com.novakai.orchestrator.engine.spi.StepExecutor;
import com.novakai.orchestrator.engine.spi.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.client.keyverifier.DefaultKnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class SftpStepExecutor implements StepExecutor {

    private final JsonParser jsonParser;

    // Track running clients for graceful shutdown
    private final Set<SshClient> runningClients = ConcurrentHashMap.newKeySet();

    @Value("${orchestrator.sftp.known-hosts-file}")
    private String knownHostsFile;

    public SftpStepExecutor(JsonParser jsonParser) {
        this.jsonParser = jsonParser;
    }

    @Override
    public String getType() {
        return "SFTP";
    }

    @Override
    public StepConfigSchema getConfigSchema() {
        return new StepConfigSchema("SFTP", "SFTP Transfer", List.of(
            new FieldDefinition("host", "Host", FieldType.STRING, true, null, null, "SSH server hostname or IP"),
            new FieldDefinition("port", "Port", FieldType.NUMBER, false, 22, null, "SSH port (default: 22)"),
            new FieldDefinition("username", "Username", FieldType.STRING, true, null, null, "SSH username for authentication"),
            new FieldDefinition("credentialRef", "Credential Reference", FieldType.SECRET_REF, true, null, null, "Reference to stored SSH key or password credential"),
            new FieldDefinition("remoteDir", "Remote Directory", FieldType.STRING, true, null, null, "Remote directory path for transfer"),
            new FieldDefinition("filePattern", "File Pattern", FieldType.FILE_PATTERN, true, null, null, "Glob pattern for files to transfer"),
            new FieldDefinition("direction", "Direction", FieldType.ENUM, true, null, List.of("UPLOAD", "DOWNLOAD"), "Transfer direction: UPLOAD or DOWNLOAD"),
            new FieldDefinition("remoteFileName", "Remote File Name Template", FieldType.STRING, false, null, null, "Template for remote file naming (${fileName}, ${fileExtension}, ${timestamp})"),
            new FieldDefinition("connectionTimeoutSeconds", "Connection Timeout", FieldType.NUMBER, false, 30, null, "SSH connection timeout in seconds"),
            new FieldDefinition("authTimeoutSeconds", "Auth Timeout", FieldType.NUMBER, false, 30, null, "SSH authentication timeout in seconds")
        ));
    }

    @Override
    public StepResult execute(StepContext ctx) throws Exception {
        long start = System.nanoTime();
        SftpConfig config = jsonParser.parse(ctx.getStepConfig(), SftpConfig.class);

        if (config == null) {
            return StepResult.failure("SftpConfig is null or empty", Duration.ofNanos(System.nanoTime() - start));
        }

        // Validate direction configuration
        String direction = config.direction();
        if (direction == null || (!"UPLOAD".equalsIgnoreCase(direction) && !"DOWNLOAD".equalsIgnoreCase(direction))) {
            return StepResult.failure("Invalid or missing direction: must be 'UPLOAD' or 'DOWNLOAD'",
                Duration.ofNanos(System.nanoTime() - start));
        }
        boolean isUpload = "UPLOAD".equalsIgnoreCase(direction);

        StringBuilder output = new StringBuilder();

        String decryptedValue = ctx.getCredentials().resolve(config.credentialRef());

        // Get timeout values with defaults
        int connectionTimeoutSec = config.connectionTimeoutSeconds() != null ? config.connectionTimeoutSeconds() : 30;
        int authTimeoutSec = config.authTimeoutSeconds() != null ? config.authTimeoutSeconds() : 30;

        if (isUpload) {
            output.append(doUpload(ctx, config, decryptedValue, connectionTimeoutSec, authTimeoutSec));
        } else {
            output.append(doDownload(ctx, config, decryptedValue, connectionTimeoutSec, authTimeoutSec));
        }

        return StepResult.success(Map.of(), output.toString(), Duration.ofNanos(System.nanoTime() - start));
    }

    private String doUpload(StepContext ctx, SftpConfig config, String decryptedValue,
                            int connectionTimeoutSec, int authTimeoutSec) throws Exception {
        StringBuilder output = new StringBuilder();

        PathMatcher matcher = java.nio.file.FileSystems.getDefault()
            .getPathMatcher("glob:" + config.filePattern());

        List<Path> files;
        try (var stream = Files.list(Paths.get(ctx.getWorkingDir()))) {
            files = stream.filter(p -> matcher.matches(p.getFileName())).toList();
        }

        if (files.isEmpty()) {
            log.debug("SFTP: no files matched pattern {} in {}", config.filePattern(), ctx.getWorkingDir());
            output.append("No files matched pattern: ").append(config.filePattern()).append("\n");
            return output.toString();
        }

        SshClient client = SshClient.setUpDefaultClient();
        configureHostKeyVerifier(client);

        Iterable<KeyPair> keyPairs = loadKeyIdentities(decryptedValue);
        client.setKeyIdentityProvider(session -> keyPairs);

        client.start();
        runningClients.add(client);

        ClientSession session = null;
        SftpClient sftp = null;

        try {
            session = client.connect(config.username(), config.host(), config.port())
                .verify(connectionTimeoutSec, TimeUnit.SECONDS)
                .getSession();
            session.addPasswordIdentity(decryptedValue);
            session.auth().verify(authTimeoutSec, TimeUnit.SECONDS);

            sftp = SftpClientFactory.instance().createSftpClient(session);

            List<String> failedFiles = new ArrayList<>();
            long totalBytes = 0;

            for (Path file : files) {
                if (ctx.isCancelRequested() || Thread.currentThread().isInterrupted()) {
                    log.info("Upload cancelled by user");
                    output.append("Upload cancelled by user\n");
                    return output.toString();
                }

                String originalName = file.getFileName().toString();
                String remoteName = config.remoteFileName() != null && !config.remoteFileName().isBlank()
                        ? resolveRemoteFileName(config.remoteFileName(), originalName)
                        : originalName;
                String remotePath = config.remoteDir() + "/" + remoteName;
                try {
                    long fileSize = Files.size(file);
                    sftp.put(file, remotePath, List.of(
                        SftpClient.OpenMode.Write,
                        SftpClient.OpenMode.Create,
                        SftpClient.OpenMode.Truncate
                    ));
                    totalBytes += fileSize;
                    output.append("Uploaded: ").append(originalName)
                       .append(" -> ").append(remoteName)
                       .append(" (").append(String.format("%.2f", fileSize / 1024.0)).append(" KB)\n");
                } catch (IOException e) {
                    String errorMsg = "Failed to upload " + originalName + ": " + e.getMessage();
                    log.warn(errorMsg);
                    failedFiles.add(originalName);
                }
            }

            output.append("\nUpload Summary: ")
                  .append(files.size() - failedFiles.size())
                  .append("/").append(files.size())
                  .append(" files successful\n");

            if (!failedFiles.isEmpty()) {
                output.append("Failed files: ").append(String.join(", ", failedFiles)).append("\n");
            }

        } finally {
            try { if (sftp != null) sftp.close(); } catch (IOException e) { log.warn("Error closing SFTP client", e); }
            if (session != null) session.close(false);
            try { client.stop(); } finally { runningClients.remove(client); }
        }

        return output.toString();
    }

    private String doDownload(StepContext ctx, SftpConfig config, String decryptedValue,
                              int connectionTimeoutSec, int authTimeoutSec) throws Exception {
        StringBuilder output = new StringBuilder();

        SshClient client = SshClient.setUpDefaultClient();
        configureHostKeyVerifier(client);

        Iterable<KeyPair> keyPairs = loadKeyIdentities(decryptedValue);
        client.setKeyIdentityProvider(session -> keyPairs);

        client.start();
        runningClients.add(client);

        ClientSession session = null;
        SftpClient sftp = null;

        try {
            session = client.connect(config.username(), config.host(), config.port())
                .verify(connectionTimeoutSec, TimeUnit.SECONDS)
                .getSession();
            session.addPasswordIdentity(decryptedValue);
            session.auth().verify(authTimeoutSec, TimeUnit.SECONDS);

            sftp = SftpClientFactory.instance().createSftpClient(session);

            String remoteDir = config.remoteDir();
            if (!remoteDir.endsWith("/")) {
                remoteDir += "/";
            }

            List<String> matchedFiles = new ArrayList<>();
            var entries = sftp.readDir(remoteDir);
            for (var entry : entries) {
                String filename = entry.getFilename();
                if (".".equals(filename) || "..".equals(filename)) continue;
                PathMatcher matcher = java.nio.file.FileSystems.getDefault()
                    .getPathMatcher("glob:" + config.filePattern());
                if (matcher.matches(Path.of(filename))) {
                    matchedFiles.add(filename);
                }
            }

            if (matchedFiles.isEmpty()) {
                log.debug("SFTP: no files matched pattern {} in remote dir {}", config.filePattern(), config.remoteDir());
                output.append("No files matched pattern: ").append(config.filePattern())
                      .append(" in remote directory: ").append(config.remoteDir()).append("\n");
                return output.toString();
            }

            Path localDir = Paths.get(ctx.getWorkingDir());
            Files.createDirectories(localDir);

            List<String> failedDownloads = new ArrayList<>();
            long totalBytes = 0;

            for (String filename : matchedFiles) {
                if (ctx.isCancelRequested() || Thread.currentThread().isInterrupted()) {
                    log.info("Download cancelled by user");
                    output.append("Download cancelled by user\n");
                    return output.toString();
                }

                String remotePath = config.remoteDir() + "/" + filename;
                Path localPath = localDir.resolve(filename);
                try {
                    try (InputStream in = sftp.read(remotePath)) {
                        Files.copy(in, localPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    long fileSize = Files.size(localPath);
                    totalBytes += fileSize;
                    output.append("Downloaded: ").append(filename)
                       .append(" (").append(String.format("%.2f", fileSize / 1024.0)).append(" KB)\n");
                } catch (IOException e) {
                    String errorMsg = "Failed to download " + filename + ": " + e.getMessage();
                    log.warn(errorMsg);
                    failedDownloads.add(filename);
                }
            }

            output.append("\nDownload Summary: ")
                  .append(matchedFiles.size() - failedDownloads.size())
                  .append("/").append(matchedFiles.size())
                  .append(" files successful\n");

            if (!failedDownloads.isEmpty()) {
                output.append("Failed downloads: ").append(String.join(", ", failedDownloads)).append("\n");
            }

        } finally {
            try { if (sftp != null) sftp.close(); } catch (IOException e) { log.warn("Error closing SFTP client", e); }
            if (session != null) session.close(false);
            try { client.stop(); } finally { runningClients.remove(client); }
        }

        return output.toString();
    }

    private String resolveRemoteFileName(String template, String originalName) {
        String nameWithoutExt = originalName.contains(".")
                ? originalName.substring(0, originalName.lastIndexOf('.'))
                : originalName;
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : "";

        return template.replace("${fileName}", nameWithoutExt)
                       .replace("${fileExtension}", extension)
                       .replace("${timestamp}", String.valueOf(System.currentTimeMillis()));
    }

    private void configureHostKeyVerifier(SshClient client) {
        if (knownHostsFile == null || knownHostsFile.isBlank()) {
            log.warn("SFTP: no known-hosts-file configured; host key verification disabled");
            return;
        }
        Path knownHostsPath = Paths.get(knownHostsFile);
        if (!Files.exists(knownHostsPath)) {
            log.warn("SFTP: known_hosts file not found at {}; host key verification disabled", knownHostsFile);
            return;
        }
        client.setServerKeyVerifier(new DefaultKnownHostsServerKeyVerifier(RejectAllServerKeyVerifier.INSTANCE, true, knownHostsPath));
    }

    private Iterable<KeyPair> loadKeyIdentities(String decryptedValue) throws Exception {
        Path potentialPath = Paths.get(decryptedValue);
        if (Files.exists(potentialPath)) {
            log.debug("SFTP: loading SSH key from file {}", decryptedValue);
            return SecurityUtils.loadKeyPairIdentities(
                null, null, Files.newInputStream(potentialPath), null);
        }

        log.debug("SFTP: treating credential value as PEM-encoded key content");
        return SecurityUtils.loadKeyPairIdentities(
            null, null, new java.io.ByteArrayInputStream(decryptedValue.getBytes(java.nio.charset.StandardCharsets.UTF_8)), null);
    }

    @PreDestroy
    public void shutdown() {
        if (!runningClients.isEmpty()) {
            log.info("Closing {} running SFTP clients on shutdown", runningClients.size());
            for (SshClient client : runningClients) {
                try {
                    client.stop();
                    log.debug("Stopped SFTP client");
                } catch (Exception e) {
                    log.warn("Error stopping SFTP client: {}", e.getMessage());
                }
            }
        }
    }
}
