# Phase 2 — Job Execution Engine

> **Goal:** Build the core engine that reads a `JobDefinition` from the DB and executes
> each step sequentially on Linux. All shell interaction goes through `ProcessBuilder`.
> No business logic in the API layer yet — just the engine.

---

## 2.1 Engine Architecture

```
JobLaunchService
  └── JobExecutionOrchestrator          ← coordinates the run lifecycle
        ├── ExecutionContext             ← shared state for the run (env, dirs, logs)
        ├── StepExecutorFactory         ← resolves executor by StepType
        │
        ├── EnvSetupStepExecutor
        ├── LogCleanupStepExecutor
        ├── JavaExecStepExecutor
        ├── SftpStepExecutor
        └── ArchiveStepExecutor
```

### Package Layout

```
com.yourco.orchestrator.engine/
├── JobLaunchService.java
├── JobExecutionOrchestrator.java
├── ExecutionContext.java
├── StepResult.java
├── StepExecutor.java               ← interface
├── StepExecutorFactory.java
└── executors/
    ├── EnvSetupStepExecutor.java
    ├── LogCleanupStepExecutor.java
    ├── JavaExecStepExecutor.java
    ├── SftpStepExecutor.java
    └── ArchiveStepExecutor.java
```

---

## 2.2 Core Interfaces & Models

### `StepExecutor.java`

```java
public interface StepExecutor {

    StepType getSupportedType();

    StepResult execute(ExecutionContext ctx, JobStep step) throws Exception;
}
```

### `StepResult.java`

```java
public record StepResult(
    boolean success,
    int exitCode,        // 0 for non-process steps on success, -1 on exception
    String logOutput
) {
    public static StepResult success(String log) {
        return new StepResult(true, 0, log);
    }

    public static StepResult failure(int exitCode, String log) {
        return new StepResult(false, exitCode, log);
    }

    public static StepResult failure(String log) {
        return new StepResult(false, -1, log);
    }
}
```

### `ExecutionContext.java`

```java
@Data @Builder
public class ExecutionContext {
    private Long runId;
    private Long jobId;
    private String workingDir;
    private Map<String, String> envVars;          // resolved: global + job-level
    private String javaHome;                      // set by EnvSetupStepExecutor
    private List<String> classpath;               // set by EnvSetupStepExecutor
    private ConcurrentLinkedQueue<String> liveLogQueue; // SSE drain target
    private volatile boolean cancelRequested;
}
```

### `StepExecutorFactory.java`

```java
@Component
public class StepExecutorFactory {

    private final Map<StepType, StepExecutor> executorMap;

    public StepExecutorFactory(List<StepExecutor> executors) {
        this.executorMap = executors.stream()
            .collect(Collectors.toMap(StepExecutor::getSupportedType, e -> e));
    }

    public StepExecutor resolve(StepType type) {
        StepExecutor executor = executorMap.get(type);
        if (executor == null) {
            throw new IllegalArgumentException("No executor registered for step type: " + type);
        }
        return executor;
    }
}
```

---

## 2.3 JobLaunchService

Entry point for triggering any job run.

```java
@Service
@Slf4j
public class JobLaunchService {

    private final JobDefinitionRepository jobRepo;
    private final JobEnvVarRepository envVarRepo;
    private final JobRunRepository runRepo;
    private final JobExecutionOrchestrator orchestrator;
    private final TaskExecutor taskExecutor;

    // Map of runId → Future, to support cancellation
    private final ConcurrentHashMap<Long, Future<?>> activeFutures = new ConcurrentHashMap<>();
    // Map of runId → live log queue, drained by SSE endpoint
    private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<String>> liveLogQueues
            = new ConcurrentHashMap<>();

    public JobRun launch(Long jobId, TriggerType triggerType, String triggeredBy) {
        JobDefinition job = jobRepo.findById(jobId)
            .orElseThrow(() -> new JobNotFoundException(jobId));

        // Prevent duplicate runs
        if (runRepo.existsByJobDefinition_JobIdAndStatus(jobId, RunStatus.RUNNING)) {
            throw new JobAlreadyRunningException(jobId);
        }

        // Build env from global vars + job-specific vars (job overrides global)
        Map<String, String> env = buildEnvMap(jobId);

        // Create run record
        JobRun run = JobRun.builder()
            .jobDefinition(job)
            .triggerType(triggerType)
            .triggeredBy(triggeredBy)
            .status(RunStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .build();
        run = runRepo.save(run);
        final Long runId = run.getRunId();

        // Set up live log queue
        ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();
        liveLogQueues.put(runId, logQueue);

        // Build execution context
        ExecutionContext ctx = ExecutionContext.builder()
            .runId(runId)
            .jobId(jobId)
            .workingDir(job.getWorkingDir())
            .envVars(env)
            .liveLogQueue(logQueue)
            .cancelRequested(false)
            .build();

        // Submit async
        Future<?> future = taskExecutor.submit(
            () -> orchestrator.execute(ctx, job, run)
        );
        activeFutures.put(runId, future);

        return run;
    }

    public void cancel(Long runId) {
        Future<?> future = activeFutures.get(runId);
        if (future != null) {
            future.cancel(true);
        }
        // Orchestrator checks cancelRequested flag too
        // The run status update happens inside the orchestrator's finally block
    }

    public ConcurrentLinkedQueue<String> getLiveLogQueue(Long runId) {
        return liveLogQueues.get(runId);
    }

    private Map<String, String> buildEnvMap(Long jobId) {
        Map<String, String> env = new LinkedHashMap<>(System.getenv()); // start with OS env
        // Layer 1: global vars
        envVarRepo.findByIsGlobal("Y").forEach(v -> env.put(v.getVarName(), v.getVarValue()));
        // Layer 2: job-specific vars (overrides globals)
        envVarRepo.findByJobDefinition_JobId(jobId)
            .stream().filter(v -> "N".equals(v.getIsGlobal()))
            .forEach(v -> env.put(v.getVarName(), v.getVarValue()));
        return env;
    }
}
```

---

## 2.4 JobExecutionOrchestrator

```java
@Component
@Slf4j
public class JobExecutionOrchestrator {

    private final JobRunRepository runRepo;
    private final JobRunStepRepository runStepRepo;
    private final StepExecutorFactory executorFactory;
    private final ObjectMapper objectMapper;

    public void execute(ExecutionContext ctx, JobDefinition job, JobRun run) {
        // Mark run as RUNNING
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(LocalDateTime.now());
        runRepo.save(run);

        boolean anyStepFailed = false;

        try {
            List<JobStep> steps = job.getSteps().stream()
                .filter(s -> "Y".equals(s.getEnabled()))
                .toList();

            for (JobStep step : steps) {
                if (ctx.isCancelRequested() || Thread.currentThread().isInterrupted()) {
                    log.info("Run {} cancelled before step {}", ctx.getRunId(), step.getStepName());
                    break;
                }

                JobRunStep runStep = createRunStep(run, step);

                try {
                    runStep.setStatus(RunStatus.RUNNING);
                    runStep.setStartedAt(LocalDateTime.now());
                    runStepRepo.save(runStep);

                    StepExecutor executor = executorFactory.resolve(step.getStepType());
                    StepResult result = executor.execute(ctx, step);

                    runStep.setExitCode(result.exitCode());
                    runStep.setLogOutput(result.logOutput());
                    runStep.setEndedAt(LocalDateTime.now());

                    if (result.success()) {
                        runStep.setStatus(RunStatus.SUCCESS);
                    } else {
                        runStep.setStatus(RunStatus.FAILED);
                        anyStepFailed = true;
                        if ("N".equals(step.getContinueOnFailure())) {
                            runStepRepo.save(runStep);
                            log.error("Step {} failed and continueOnFailure=N. Aborting run.", step.getStepName());
                            break;
                        }
                    }
                } catch (Exception ex) {
                    log.error("Unexpected error in step {}: {}", step.getStepName(), ex.getMessage(), ex);
                    runStep.setStatus(RunStatus.FAILED);
                    runStep.setLogOutput("EXCEPTION: " + ex.getMessage());
                    runStep.setEndedAt(LocalDateTime.now());
                    anyStepFailed = true;
                    if ("N".equals(step.getContinueOnFailure())) {
                        runStepRepo.save(runStep);
                        break;
                    }
                } finally {
                    runStepRepo.save(runStep);
                }
            }

        } finally {
            run.setEndedAt(LocalDateTime.now());
            if (ctx.isCancelRequested() || Thread.currentThread().isInterrupted()) {
                run.setStatus(RunStatus.CANCELLED);
            } else {
                run.setStatus(anyStepFailed ? RunStatus.PARTIAL : RunStatus.SUCCESS);
            }
            runRepo.save(run);
        }
    }

    private JobRunStep createRunStep(JobRun run, JobStep step) {
        return JobRunStep.builder()
            .jobRun(run)
            .jobStep(step)
            .stepOrder(step.getStepOrder())
            .status(RunStatus.PENDING)
            .build();
    }
}
```

---

## 2.5 Step Executors

### EnvSetupStepExecutor

```java
@Component
public class EnvSetupStepExecutor implements StepExecutor {

    @Override public StepType getSupportedType() { return StepType.ENV_SETUP; }

    @Override
    public StepResult execute(ExecutionContext ctx, JobStep step) throws Exception {
        StringBuilder log = new StringBuilder();
        EnvSetupConfig config = parse(step.getStepConfig(), EnvSetupConfig.class);

        // Validate JAVA_HOME
        Path javaHome = Path.of(config.javaHome());
        if (!Files.isDirectory(javaHome)) {
            return StepResult.failure("JAVA_HOME does not exist: " + javaHome);
        }
        Path javaBin = javaHome.resolve("bin/java");
        if (!Files.isExecutable(javaBin)) {
            return StepResult.failure("java binary not executable: " + javaBin);
        }
        log.append("JAVA_HOME validated: ").append(javaHome).append("\n");

        // Inject into context
        ctx.setJavaHome(config.javaHome());
        ctx.setClasspath(new ArrayList<>(config.classpathEntries()));

        // Validate classpath entries exist
        for (String entry : config.classpathEntries()) {
            if (!Files.exists(Path.of(entry))) {
                log.append("WARNING: classpath entry not found: ").append(entry).append("\n");
            }
        }

        // Merge extra env vars into context
        if (config.extraEnvVars() != null) {
            ctx.getEnvVars().putAll(config.extraEnvVars());
            log.append("Merged ").append(config.extraEnvVars().size()).append(" extra env vars\n");
        }

        return StepResult.success(log.toString());
    }
}
```

### LogCleanupStepExecutor

```java
@Component
public class LogCleanupStepExecutor implements StepExecutor {

    @Override public StepType getSupportedType() { return StepType.LOG_CLEANUP; }

    @Override
    public StepResult execute(ExecutionContext ctx, JobStep step) throws Exception {
        StringBuilder log = new StringBuilder();
        LogCleanupConfig config = parse(step.getStepConfig(), LogCleanupConfig.class);

        Path dir = resolveDir(ctx.getWorkingDir(), config.directory());
        if (!Files.isDirectory(dir)) {
            return StepResult.failure("Cleanup directory does not exist: " + dir);
        }

        PathMatcher matcher = FileSystems.getDefault()
            .getPathMatcher("glob:" + config.filePattern());

        int deleted = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                if (matcher.matches(file.getFileName())) {
                    Files.delete(file);
                    log.append("Deleted: ").append(file.getFileName()).append("\n");
                    deleted++;
                }
            }
        }

        log.append("Total files deleted: ").append(deleted).append("\n");
        return StepResult.success(log.toString());
    }

    private Path resolveDir(String workingDir, String dir) {
        Path d = Path.of(dir);
        return d.isAbsolute() ? d : Path.of(workingDir).resolve(d);
    }
}
```

### JavaExecStepExecutor

```java
@Component
public class JavaExecStepExecutor implements StepExecutor {

    @Value("${orchestrator.engine.default-step-timeout-minutes:60}")
    private int defaultTimeoutMinutes;

    @Override public StepType getSupportedType() { return StepType.JAVA_EXEC; }

    @Override
    public StepResult execute(ExecutionContext ctx, JobStep step) throws Exception {
        JavaExecConfig config = parse(step.getStepConfig(), JavaExecConfig.class);
        StringBuilder log = new StringBuilder();

        // Build command
        String javaBin = ctx.getJavaHome() + "/bin/java";
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.addAll(config.jvmArgs() != null ? config.jvmArgs() : List.of());

        if (config.jarPath() != null && !config.jarPath().isBlank()) {
            command.add("-jar");
            command.add(config.jarPath());
        } else {
            // Classpath mode
            if (ctx.getClasspath() != null && !ctx.getClasspath().isEmpty()) {
                command.add("-cp");
                command.add(String.join(":", ctx.getClasspath()));
            }
            command.add(config.mainClass());
        }

        if (config.args() != null) {
            command.addAll(config.args());
        }

        log.append("Executing: ").append(String.join(" ", command)).append("\n");
        ctx.getLiveLogQueue().add("Executing: " + String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(ctx.getWorkingDir()));
        pb.environment().putAll(ctx.getEnvVars());
        pb.redirectErrorStream(true);   // merge stderr into stdout

        Process process = pb.start();
        int timeout = config.timeoutMinutes() != null ? config.timeoutMinutes() : defaultTimeoutMinutes;

        // Stream output in real time
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.append(line).append("\n");
                ctx.getLiveLogQueue().add(line);   // feeds SSE stream
            }
        }

        boolean completed = process.waitFor(timeout, TimeUnit.MINUTES);
        if (!completed) {
            process.destroyForcibly();
            return StepResult.failure(-1, log + "\nPROCESS TIMED OUT after " + timeout + " minutes");
        }

        int exitCode = process.exitValue();
        log.append("\nProcess exited with code: ").append(exitCode);

        return exitCode == 0
            ? StepResult.success(log.toString())
            : StepResult.failure(exitCode, log.toString());
    }
}
```

### SftpStepExecutor

```java
@Component
public class SftpStepExecutor implements StepExecutor {

    private final JobCredentialRepository credentialRepo;
    private final CredentialDecryptionService decryptionService;

    @Override public StepType getSupportedType() { return StepType.SFTP; }

    @Override
    public StepResult execute(ExecutionContext ctx, JobStep step) throws Exception {
        SftpConfig config = parse(step.getStepConfig(), SftpConfig.class);
        StringBuilder log = new StringBuilder();

        // Resolve credential
        JobCredential cred = credentialRepo.findByCredentialRef(config.credentialRef())
            .orElseThrow(() -> new RuntimeException("Credential not found: " + config.credentialRef()));
        String decryptedValue = decryptionService.decrypt(cred.getCredValue());

        // Find files to transfer
        PathMatcher matcher = FileSystems.getDefault()
            .getPathMatcher("glob:" + config.filePattern());
        List<Path> files;
        try (Stream<Path> stream = Files.list(Path.of(ctx.getWorkingDir()))) {
            files = stream.filter(p -> matcher.matches(p.getFileName())).toList();
        }

        if (files.isEmpty()) {
            log.append("No files matched pattern: ").append(config.filePattern()).append("\n");
            return StepResult.success(log.toString());
        }

        // Connect via Apache MINA SSHD
        try (SshClient client = SshClient.setUpDefaultClient()) {
            client.start();
            ConnectFuture cf = client.connect(config.username(), config.host(), config.port());
            cf.await(30, TimeUnit.SECONDS);

            try (ClientSession session = cf.getSession()) {
                if (cred.getCredType() == CredentialType.SSH_KEY) {
                    session.addPublicKeyIdentity(loadKeyPair(decryptedValue));
                } else {
                    session.addPasswordIdentity(decryptedValue);
                }
                session.auth().verify(30, TimeUnit.SECONDS);

                try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
                    for (Path file : files) {
                        String remotePath = config.remoteDir() + "/" + file.getFileName();
                        try (InputStream in = Files.newInputStream(file)) {
                            sftp.write(remotePath, in);
                            long bytes = Files.size(file);
                            log.append("Uploaded: ").append(file.getFileName())
                               .append(" (").append(bytes / 1024).append(" KB)\n");
                        }
                    }
                }
            }
        }

        return StepResult.success(log.toString());
    }
}
```

### ArchiveStepExecutor

```java
@Component
public class ArchiveStepExecutor implements StepExecutor {

    @Override public StepType getSupportedType() { return StepType.ARCHIVE; }

    @Override
    public StepResult execute(ExecutionContext ctx, JobStep step) throws Exception {
        ArchiveConfig config = parse(step.getStepConfig(), ArchiveConfig.class);
        StringBuilder log = new StringBuilder();

        // Collect files matching any of the patterns
        Path sourceDir = Path.of(config.sourceDir());
        List<Path> filesToArchive = new ArrayList<>();
        for (String pattern : config.filePatterns()) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir)) {
                for (Path file : stream) {
                    if (matcher.matches(file.getFileName())) filesToArchive.add(file);
                }
            }
        }

        if (filesToArchive.isEmpty()) {
            log.append("No files matched patterns for archiving\n");
            return StepResult.success(log.toString());
        }

        // Build archive filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String jobName = ctx.getEnvVars().getOrDefault("JOB_NAME", "job");
        String ext = "TAR_GZ".equals(config.archiveFormat()) ? ".tar.gz" : ".zip";
        Path archiveDir = Path.of(config.archiveDir());
        Files.createDirectories(archiveDir);
        Path archivePath = archiveDir.resolve(jobName + "_" + timestamp + ext);

        // Write archive
        if ("TAR_GZ".equals(config.archiveFormat())) {
            writeTarGz(archivePath, filesToArchive, log);
        } else {
            writeZip(archivePath, filesToArchive, log);
        }

        log.append("Archive created: ").append(archivePath).append("\n");
        return StepResult.success(log.toString());
    }

    private void writeZip(Path archivePath, List<Path> files, StringBuilder log) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archivePath))) {
            for (Path file : files) {
                zos.putNextEntry(new ZipEntry(file.getFileName().toString()));
                Files.copy(file, zos);
                zos.closeEntry();
                log.append("Archived: ").append(file.getFileName()).append("\n");
            }
        }
    }

    private void writeTarGz(Path archivePath, List<Path> files, StringBuilder log) throws IOException {
        try (GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(
                Files.newOutputStream(archivePath));
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            for (Path file : files) {
                TarArchiveEntry entry = new TarArchiveEntry(file.toFile(), file.getFileName().toString());
                tar.putArchiveEntry(entry);
                Files.copy(file, tar);
                tar.closeArchiveEntry();
                log.append("Archived: ").append(file.getFileName()).append("\n");
            }
        }
    }
}
```

---

## 2.6 Async Thread Pool Configuration

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${orchestrator.engine.thread-pool-size:10}")
    private int poolSize;

    @Bean(name = "jobTaskExecutor")
    public TaskExecutor jobTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize * 2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("job-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

---

## 2.7 Credential Encryption Service

```java
@Service
public class CredentialDecryptionService {

    // Key must be 32 bytes (256 bits) for AES-256
    // Read from environment variable — never from DB or config file
    @Value("${ORCHESTRATOR_ENCRYPTION_KEY}")
    private String encryptionKey;

    public String encrypt(String plainText) throws Exception {
        SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        // Prepend IV to ciphertext, then Base64
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    public String decrypt(String encryptedBase64) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedBase64);
        byte[] iv = Arrays.copyOfRange(combined, 0, 12);
        byte[] cipherText = Arrays.copyOfRange(combined, 12, combined.length);
        SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }
}
```

---

## Phase 2 Acceptance Criteria

- [ ] All 5 step types execute without error against a test job on Linux
- [ ] `JavaExecStepExecutor` streams stdout/stderr in real time to `liveLogQueue`
- [ ] `LogCleanupStepExecutor` correctly matches and deletes files using glob patterns
- [ ] `SftpStepExecutor` successfully uploads at least one test file to a local SFTP server
- [ ] `ArchiveStepExecutor` produces a valid ZIP and TAR_GZ archive
- [ ] A run with `continueOnFailure=N` on a failing step stops subsequent steps
- [ ] Cancellation via `cancel(runId)` stops a long-running Java process within 5 seconds
- [ ] Credential encrypt/decrypt round-trip passes unit test

---

**Previous:** [Phase 1 — Foundation](./PHASE-1-Foundation.md)  
**Next:** [Phase 3 — REST API](./PHASE-3-API.md)
