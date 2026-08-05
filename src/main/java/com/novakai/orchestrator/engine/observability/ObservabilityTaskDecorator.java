package com.novakai.orchestrator.engine.observability;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Task decorator that clears MDC before and after each runnable to prevent
 * context leakage across steps executed on the same thread-pool thread.
 */
public class ObservabilityTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return () -> {
            // Clear any leftover MDC from previous task on this thread
            MDC.clear();
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
