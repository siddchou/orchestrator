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

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

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
        ConcurrentLinkedQueue<String> queue = launchService.getLiveLogQueue(runId);

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
                    String line = queue.poll();
                    if (line != null) {
                        emitter.send(SseEmitter.event().data(line));
                    } else {
                        JobRun run = runRepo.findById(runId).orElse(null);
                        if (run != null && run.getStatus() != RunStatus.RUNNING
                                        && run.getStatus() != RunStatus.PENDING) {
                            String remaining;
                            while ((remaining = queue.poll()) != null) {
                                emitter.send(SseEmitter.event().data(remaining));
                            }
                            emitter.send(SseEmitter.event().name("done").data("RUN_COMPLETE"));
                            emitter.complete();
                            break;
                        }
                        Thread.sleep(250);
                    }
                }
            } catch (java.io.IOException ex) {
                log.debug("SSE client disconnected for run {}", runId);
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