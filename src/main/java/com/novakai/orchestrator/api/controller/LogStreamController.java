package com.novakai.orchestrator.api.controller;

import com.novakai.orchestrator.domain.entity.JobRun;
import com.novakai.orchestrator.domain.enums.RunStatus;
import com.novakai.orchestrator.engine.JobLaunchService;
import com.novakai.orchestrator.repository.JobRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class LogStreamController {

    private final JobLaunchService launchService;
    private final JobRunRepository runRepo;

    @GetMapping(value = "/runs/{runId}/log-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLog(@PathVariable Long runId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        BlockingQueue<String> queue = launchService.getLiveLogQueue(runId);

        if (queue == null) {
            try {
                emitter.send(SseEmitter.event().name("done").data("RUN_ALREADY_COMPLETE"));
            } catch (java.io.IOException ignored) { }
            emitter.complete();
            return emitter;
        }

        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        Thread.startVirtualThread(() -> {
            MDC.setContextMap(contextMap);
            try {
                while (true) {
                    // Use take() with timeout to block efficiently instead of busy-looping
                    String line = queue.poll(1, TimeUnit.SECONDS);
                    if (line != null) {
                        emitter.send(SseEmitter.event().data(line));
                    } else {
                        // No log available in the last second - check if job is complete
                        JobRun run = runRepo.findById(runId).orElse(null);
                        if (run == null || run.getStatus() != RunStatus.RUNNING) {
                            // Drain remaining logs before completing
                            java.util.List<String> lines = new ArrayList<>();
                            queue.drainTo(lines);
                            for (String l : lines) {
                                emitter.send(SseEmitter.event().data(l));
                            }
                            emitter.send(SseEmitter.event().name("done").data(
                                run != null && run.getStatus() == RunStatus.RUNNING
                                    ? "RUN_COMPLETE"
                                    : "JOB_NOT_FOUND"));
                            emitter.complete();
                            break;
                        }
                    }
                }
            } catch (java.io.IOException ex) {
                log.debug("SSE client disconnected for run {}", runId);
                emitter.completeWithError(ex);
            } catch (InterruptedException ex) {
                log.debug("Log stream thread interrupted for run {}", runId);
                Thread.currentThread().interrupt();
                emitter.completeWithError(ex);
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            } finally {
                MDC.clear();
            }
        });

        return emitter;
    }
}