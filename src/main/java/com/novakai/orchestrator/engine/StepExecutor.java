package com.novakai.orchestrator.engine;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.entity.JobStep;
import com.novakai.orchestrator.domain.enums.StepType;

public interface StepExecutor {

    StepType getSupportedType();

    StepResult execute(ExecutionContext ctx, JobStep step) throws Exception;
}
