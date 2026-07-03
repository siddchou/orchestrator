package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.enums.StepType;
import com.novakai.orchestrator.engine.executors.ArchiveStepExecutor;
import com.novakai.orchestrator.engine.executors.EnvSetupStepExecutor;
import com.novakai.orchestrator.engine.executors.JavaExecStepExecutor;
import com.novakai.orchestrator.engine.executors.LogCleanupStepExecutor;
import com.novakai.orchestrator.engine.executors.SftpStepExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class StepExecutorFactoryTest {

    @Autowired
    private StepExecutorFactory factory;

    @Test
    void resolve_ENV_SETUP() {
        StepExecutor executor = factory.resolve(StepType.ENV_SETUP);
        assertInstanceOf(EnvSetupStepExecutor.class, executor);
    }

    @Test
    void resolve_JAVA_EXEC() {
        StepExecutor executor = factory.resolve(StepType.JAVA_EXEC);
        assertInstanceOf(JavaExecStepExecutor.class, executor);
    }

    @Test
    void resolve_SFTP() {
        StepExecutor executor = factory.resolve(StepType.SFTP);
        assertInstanceOf(SftpStepExecutor.class, executor);
    }

    @Test
    void resolve_ARCHIVE() {
        StepExecutor executor = factory.resolve(StepType.ARCHIVE);
        assertInstanceOf(ArchiveStepExecutor.class, executor);
    }

    @Test
    void resolve_LOG_CLEANUP() {
        StepExecutor executor = factory.resolve(StepType.LOG_CLEANUP);
        assertInstanceOf(LogCleanupStepExecutor.class, executor);
    }
}
