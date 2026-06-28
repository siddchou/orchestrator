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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Iterator;
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

        StringBuilder log = new StringBuilder();

        JobCredential cred = credentialRepo.findByCredentialRef(config.credentialRef())
            .orElseThrow(() -> new RuntimeException("Credential not found: " + config.credentialRef()));

        String decryptedValue = decryptionService.decrypt(cred.getCredValue());

        PathMatcher matcher = java.nio.file.FileSystems.getDefault()
            .getPathMatcher("glob:" + config.filePattern());

        List<Path> files;
        try (var stream = Files.list(Paths.get(ctx.getWorkingDir()))) {
            files = stream.filter(p -> matcher.matches(p.getFileName())).toList();
        }

        if (files.isEmpty()) {
            log.append("No files matched pattern: ").append(config.filePattern()).append("\n");
            return StepResult.success(log.toString());
        }

        SshClient client = SshClient.setUpDefaultClient();

        if (cred.getCredType() == CredentialType.SSH_KEY) {
            Iterable<KeyPair> keyPairs = SecurityUtils.loadKeyPairIdentities(
                null, null, Files.newInputStream(Path.of(decryptedValue)), null);
            client.setKeyIdentityProvider(session -> keyPairs);
        }

        client.start();

        try {
            ClientSession session = client.connect(config.username(), config.host(), config.port())
                .verify(30, TimeUnit.SECONDS)
                .getSession();

            try {
                if (cred.getCredType() != CredentialType.SSH_KEY) {
                    session.addPasswordIdentity(decryptedValue);
                }
                session.auth().verify(30, TimeUnit.SECONDS);

                try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
                    for (Path file : files) {
                        String remotePath = config.remoteDir() + "/" + file.getFileName();
                        sftp.put(file, remotePath, List.of(
                            SftpClient.OpenMode.Write,
                            SftpClient.OpenMode.Create,
                            SftpClient.OpenMode.Truncate
                        ));
                        long bytes = Files.size(file);
                        log.append("Uploaded: ").append(file.getFileName())
                           .append(" (").append(bytes / 1024).append(" KB)\n");
                    }
                }
            } finally {
                session.close(false);
            }
        } finally {
            client.stop();
        }

        return StepResult.success(log.toString());
    }
}
