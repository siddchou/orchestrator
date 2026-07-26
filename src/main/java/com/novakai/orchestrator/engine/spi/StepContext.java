package com.novakai.orchestrator.engine.spi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * Runtime context passed to every step executor. Replaces ExecutionContext
 * while preserving the fields that existing code depends on.
 */
public class StepContext {

    private final Long runId;
    private final String stepId;                     // logical step identifier (name or DB id as string)
    private final String stepConfig;                 // raw JSON config from JobStep.stepConfig
    private final Map<String, Object> resolvedParams; // run-time parameters (Phase 3 templating input)
    private final CredentialResolver credentials;     // typed credential access (wraps decryption service)
    private final LogSink logSink;                    // abstraction over liveLogQueue for SSE compat
    private final Path workDir;
    private final Map<String, StepResult> upstreamOutputs; // completed sibling step results (Phase 3)

    /* --- backward-compat fields from ExecutionContext --- */
    private Long jobId;
    private String javaHome;
    private List<String> classpath;
    private Map<String, String> envVars;
    private volatile boolean cancelRequested;        // must remain volatile for interrupt check

    public StepContext(Builder builder) {
        this.runId = builder.runId;
        this.stepId = builder.stepId;
        this.stepConfig = builder.stepConfig;
        this.resolvedParams = Map.copyOf(builder.resolvedParams);
        this.credentials = builder.credentials;
        this.logSink = builder.logSink;
        this.workDir = builder.workDir;
        this.upstreamOutputs = Map.copyOf(builder.upstreamOutputs);
        this.jobId = builder.jobId;
        this.javaHome = builder.javaHome;
        this.classpath = Collections.unmodifiableList(new java.util.ArrayList<>(builder.classpath));
        this.envVars = new HashMap<>(builder.envVars); // mutable — ENV_SETUP mutates this
        this.cancelRequested = false;
    }

    // --- getters ---
    public Long getRunId() { return runId; }
    public String getStepId() { return stepId; }
    public String getStepConfig() { return stepConfig; }
    public Map<String, Object> getResolvedParams() { return resolvedParams; }
    public CredentialResolver getCredentials() { return credentials; }
    public LogSink getLogSink() { return logSink; }
    public Path getWorkDir() { return workDir; }
    public Map<String, StepResult> getUpstreamOutputs() { return upstreamOutputs; }

    // backward compat with ExecutionContext API
    public Long getJobId() { return jobId; }
    public String getJavaHome() { return javaHome; }
    public void setJavaHome(String v) { this.javaHome = v; }
    public List<String> getClasspath() { return classpath; }
    public void setClasspath(List<String> v) { this.classpath = Collections.unmodifiableList(v); }
    public Map<String, String> getEnvVars() { return envVars; }
    public BlockingQueue<String> getLiveLogQueue() { return logSink != null ? logSink.getQueue() : null; } // SSE compat bridge
    public String getWorkingDir() { return workDir != null ? workDir.toString() : null; } // backward compat bridge
    public boolean isCancelRequested() { return cancelRequested; }
    public void setCancelRequested(boolean v) { this.cancelRequested = v; }

    // --- CredentialResolver (functional interface) ---
    @FunctionalInterface
    public interface CredentialResolver {
        /** Resolve a credential reference to its decrypted value. Throws if not found. */
        String resolve(String credentialRef);
    }

    // --- LogSink (wraps BlockingQueue for SSE compatibility) ---
    public static class LogSink {
        private static final Logger LOG = LoggerFactory.getLogger(LogSink.class);
        private static final int QUEUE_WARNING_THRESHOLD = 10_000;

        private final BlockingQueue<String> queue;

        public LogSink(BlockingQueue<String> queue) {
            this.queue = queue;
        }

        public void log(String line) {
            if (queue != null && !line.isBlank()) {
                queue.add(line);
                int size = queue.size();
                if (size == QUEUE_WARNING_THRESHOLD) {
                    LOG.warn("Live log queue has {} entries — consider consuming logs faster to avoid memory pressure", size);
                } else if (size > QUEUE_WARNING_THRESHOLD && size % 10_000 == 0) {
                    LOG.warn("Live log queue still growing: {} entries", size);
                }
            }
        }

        /** Exposes the raw queue for backward compat with existing executor code. */
        public BlockingQueue<String> getQueue() { return queue; }
    }

    // --- Builder ---
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long runId, jobId;
        private String stepId, stepConfig, javaHome;
        private Map<String, Object> resolvedParams = Map.of();
        private CredentialResolver credentials;
        private LogSink logSink;
        private Path workDir;
        private Map<String, StepResult> upstreamOutputs = Map.of();
        private List<String> classpath = new java.util.ArrayList<>();
        private Map<String, String> envVars = new HashMap<>();

        public Builder runId(Long v) { this.runId = v; return this; }
        public Builder jobId(Long v) { this.jobId = v; return this; }
        public Builder stepId(String v) { this.stepId = v; return this; }
        public Builder stepConfig(String v) { this.stepConfig = v; return this; }
        public Builder resolvedParams(Map<String, Object> v) { this.resolvedParams = v; return this; }
        public Builder credentials(CredentialResolver v) { this.credentials = v; return this; }
        public Builder logSink(LogSink v) { this.logSink = v; return this; }
        public Builder workDir(Path v) { this.workDir = v; return this; }
        public Builder upstreamOutputs(Map<String, StepResult> v) { this.upstreamOutputs = v; return this; }
        public Builder javaHome(String v) { this.javaHome = v; return this; }
        public Builder classpath(List<String> v) { this.classpath = new java.util.ArrayList<>(v); return this; }
        public Builder envVars(Map<String, String> v) { this.envVars = new HashMap<>(v); return this; }

        public StepContext build() { return new StepContext(this); }
    }
}
