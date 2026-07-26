package com.novakai.orchestrator.engine.spi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry of step executors keyed by type string.
 * Replaces StepExecutorFactory with an open type system.
 */
@Component
@Slf4j
public class StepExecutorRegistry {

    private final Map<String, StepExecutor> executorMap;
    private final List<StepConfigSchema> schemas;

    public StepExecutorRegistry(List<StepExecutor> executors) {
        this.executorMap = executors.stream()
            .collect(Collectors.toMap(StepExecutor::getType, e -> e, (a, b) -> {
                log.warn("Duplicate executor for type '{}': {} wins over {}", a.getType(), b.getClass().getSimpleName(), a.getClass().getSimpleName());
                return b; // last-registered wins
            }));

        this.schemas = executors.stream()
            .map(StepExecutor::getConfigSchema)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Resolve executor by type string. */
    public Optional<StepExecutor> get(String type) {
        StepExecutor executor = executorMap.get(type);
        if (executor == null) {
            log.debug("No executor registered for step type: {}", type);
            return Optional.empty();
        }
        log.debug("Resolved executor {} for step type {}", executor.getClass().getSimpleName(), type);
        return Optional.of(executor);
    }

    /** Return config schemas for all registered executors. */
    public List<StepConfigSchema> listAll() {
        return new ArrayList<>(schemas);
    }

    /** Return the set of registered type strings. */
    public Set<String> registeredTypes() {
        return executorMap.keySet();
    }
}
