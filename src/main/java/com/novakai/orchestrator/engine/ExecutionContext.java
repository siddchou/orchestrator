package com.novakai.orchestrator.engine;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

@Data
@Builder
public class ExecutionContext {
    private Long runId;
    private Long jobId;
    private String workingDir;
    private Map<String, String> envVars;
    private String javaHome;
    private List<String> classpath;
    private BlockingQueue<String> liveLogQueue;
    private volatile boolean cancelRequested;
}
