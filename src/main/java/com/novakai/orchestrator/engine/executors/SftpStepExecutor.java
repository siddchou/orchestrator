package com.novakai.orchestrator.engine.executors;

import com.novakai.orchestrator.domain.config.SftpConfig;
import com.novakai.orchestrator.domain.entity.JobCredential;
import com.novakai.orchestrator.domain.enums.CredentialType;
import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.StepType;
import com.novakai.orchestrator.engine.ExecutionContext;
import com.novakai.orchestrator.engine.StepExecutor;
import com.novakai.orchestrator.engine.StepResult;
import com.novakai.orchestrator.engine.JsonParser;
import com.novakai.orchestrator.engine.service.CredentialDecryptionService;
import com.novakai.orchestrator.repository.JobCredentialRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class SftpStepExecutor implements StepExecutor {

    private final JobCredentialRepository credentialRepo;
    private final CredentialDecryptionService decryptionService;
    private final JsonParser jsonParser;

    public SftpStepExecutor(JobCredentialRepository credentialRepo,
                            CredentialDecryptionService decryptionService,
                            JsonParser jsonParser) {
        this.credentialRepo = credentialRepo;
        this.decryptionService = decryptionService;
        this.jsonParser = jsonParser;
    }

    @Override
    public StepType getSupportedType() {
        return StepType.SFTP;
    }

    @Override
    public StepResult execute(ExecutionContext ctx, JobStep step) throws Exception {
        SftpConfig config = jsonParser.parse(step.getStepConfig(), SftpConfig.class);

        if (config == null) {
            return StepResult.failure("SftpConfig is null or empty");
        }

        // Validate direction configuration
        String direction = config.direction();
        if (direction == null || (!"UPLOAD".equalsIgnoreCase(direction) && !"DOWNLOAD".equalsIgnoreCase(direction))) {
            return StepResult.failure("Invalid or missing direction: must be 'UPLOAD' or 'DOWNLOAD'");
        }
        boolean isUpload = "UPLOAD".equalsIgnoreCase(direction);

        StringBuilder output = new StringBuilder();

        JobCredential cred = credentialRepo.findByCredentialRef(config.credentialRef())
            .orElseThrow(() -> new RuntimeException("Credential not found: " + config.credentialRef()));

        log.debug("SFTP: credential ref={} type={}", config.credentialRef(), cred.getCredType());
        String decryptedValue = decryptionService.decrypt(cred.getCredValue());

        // Get timeout values with defaults
        int connectionTimeoutSec = config.connectionTimeoutSeconds() != null ? config.connectionTimeoutSeconds() : 30;
        int authTimeoutSec = config.authTimeoutSeconds() != null ? config.authTimeoutSeconds() : 30;

        if (isUpload) {
            output.append(doUpload(ctx, config, cred, decryptedValue, connectionTimeoutSec, authTimeoutSec));
        } else {
            output.append(doDownload(ctx, config, cred, decryptedValue, connectionTimeoutSec, authTimeoutSec));
        }

        return StepResult.success(output.toString());
    }

    private String doUpload(ExecutionContext ctx, SftpConfig config, JobCredential cred,
                            String decryptedValue, int connectionTimeoutSec, int authTimeoutSec) throws Exception {
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

        if (cred.getCredType() == CredentialType.SSH_KEY) {
            Iterable<KeyPair> keyPairs = loadKeyIdentities(decryptedValue);
            client.setKeyIdentityProvider(session -> keyPairs);
        }

        client.start();

        ClientSession session = null;
        SftpClient sftp = null;

        try {
            session = client.connect(config.username(), config.host(), config.port())
                .verify(connectionTimeoutSec, TimeUnit.SECONDS)
                .getSession();

            // Add password identity for password-based auth
            if (cred.getCredType() != CredentialType.SSH_KEY) {
                session.addPasswordIdentity(decryptedValue);
            }
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

                String remotePath = config.remoteDir() + "/" + file.getFileName();
                try {
                    long fileSize = Files.size(file);
                    sftp.put(file, remotePath, List.of(
                        SftpClient.OpenMode.Write,
                        SftpClient.OpenMode.Create,
                        SftpClient.OpenMode.Truncate
                    ));
                    totalBytes += fileSize;
                    output.append("Uploaded: ").append(file.getFileName())
                       .append(" (").append(String.format("%.2f", fileSize / 1024.0)).append(" KB)\n");
                } catch (IOException e) {
                    String errorMsg = "Failed to upload " + file.getFileName() + ": " + e.getMessage();
                    log.warn(errorMsg);
                    failedFiles.add(file.getFileName().toString());
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
            if (sftp != null) {
                try {
                    sftp.close();
                } catch (IOException e) {
                    log.warn("Error closing SFTP client", e);
                }
            }
            if (session != null) {
                session.close(false);
            }
            client.stop();
        }

        return output.toString();
    }

    private String doDownload(ExecutionContext ctx, SftpConfig config, JobCredential cred,
                              String decryptedValue, int connectionTimeoutSec, int authTimeoutSec) throws Exception {
        StringBuilder output = new StringBuilder();

        SshClient client = SshClient.setUpDefaultClient();

        if (cred.getCredType() == CredentialType.SSH_KEY) {
            Iterable<KeyPair> keyPairs = loadKeyIdentities(decryptedValue);
            client.setKeyIdentityProvider(session -> keyPairs);
        }

        client.start();

        ClientSession session = null;
        SftpClient sftp = null;

        try {
            session = client.connect(config.username(), config.host(), config.port())
                .verify(connectionTimeoutSec, TimeUnit.SECONDS)
                .getSession();

            if (cred.getCredType() != CredentialType.SSH_KEY) {
                session.addPasswordIdentity(decryptedValue);
            }
            session.auth().verify(authTimeoutSec, TimeUnit.SECONDS);

            sftp = SftpClientFactory.instance().createSftpClient(session);

            // List files in remote directory matching pattern
            String remoteDir = config.remoteDir();
            if (!remoteDir.endsWith("/")) {
                remoteDir += "/";
            }

            List<String> matchedFiles = new ArrayList<>();
            var entries = sftp.readDir(remoteDir);
            for (var entry : entries) {
                String filename = entry.getFilename();
                if (".".equals(filename) || "..".equals(filename)) {
                    continue;
                }
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
            if (sftp != null) {
                try {
                    sftp.close();
                } catch (IOException e) {
                    log.warn("Error closing SFTP client", e);
                }
            }
            if (session != null) {
                session.close(false);
            }
            client.stop();
        }

        return output.toString();
    }

    /**
     * Load KeyPair identities from either a file path or direct key content.
     * If the decryptedValue is a valid file path, it loads from the file.
     * Otherwise, it treats the value as key content (PEM format).
     */
    private Iterable<KeyPair> loadKeyIdentities(String decryptedValue) throws Exception {
        try {
            // Try to load as a file first
            return SecurityUtils.loadKeyPairIdentities(
                null, null, Files.newInputStream(Path.of(decryptedValue)), null);
        } catch (Exception e) {
            // If file loading fails, treat as direct key content (PEM format)
            log.debug("SSH key not found as file, treating as direct key content");
            return SecurityUtils.loadKeyPairIdentities(
                null, null, new java.io.ByteArrayInputStream(decryptedValue.getBytes(java.nio.charset.StandardCharsets.UTF_8)), null);
        }
    }
}
