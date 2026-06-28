package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.enums.StepType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
