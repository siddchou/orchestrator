package com.novakai.orchestrator.engine;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.enums.StepType;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
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
        log.debug("Resolved executor {} for step type {}", executor.getClass().getSimpleName(), type);
        return executor;
    }
}
