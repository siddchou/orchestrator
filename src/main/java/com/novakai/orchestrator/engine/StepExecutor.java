package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.StepType;

public interface StepExecutor {

    StepType getSupportedType();

    StepResult execute(ExecutionContext ctx, JobStep step) throws Exception;
}
