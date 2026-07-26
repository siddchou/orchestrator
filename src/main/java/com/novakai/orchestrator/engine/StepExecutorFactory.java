package com.novakai.orchestrator.engine;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.enums.StepType;
import java.util.List;

/**
 * @deprecated Replaced by {@link com.novakai.orchestrator.engine.spi.StepExecutorRegistry}.
 *   This class is retained only so that any code that references the symbol still compiles.
 *   It is not a Spring bean and should not be instantiated — all executors now implement
 *   {@code engine.spi.StepExecutor} which this old interface does not match.
 */
@Deprecated
public class StepExecutorFactory {

    private final List<StepExecutor> executors;

    public StepExecutorFactory(List<StepExecutor> executors) {
        this.executors = executors;
    }

    /**
     * @deprecated Use {@link com.novakai.orchestrator.engine.spi.StepExecutorRegistry#get(String)}.
     */
    @Deprecated
    public StepExecutor resolve(StepType type) {
        throw new UnsupportedOperationException(
            "StepExecutorFactory is deprecated — use StepExecutorRegistry.get(\"...\") instead");
    }
}
