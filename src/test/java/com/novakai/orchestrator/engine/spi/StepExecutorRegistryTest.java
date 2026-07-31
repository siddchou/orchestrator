package com.novakai.orchestrator.engine.spi;

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
class StepExecutorRegistryTest {

    @Autowired
    private StepExecutorRegistry registry;

    @Test
    void resolve_ENV_SETUP() {
        StepExecutor executor = registry.get("ENV_SETUP").orElse(null);
        assertNotNull(executor);
        assertInstanceOf(EnvSetupStepExecutor.class, executor);
    }

    @Test
    void resolve_JAVA_EXEC() {
        StepExecutor executor = registry.get("JAVA_EXEC").orElse(null);
        assertNotNull(executor);
        assertInstanceOf(JavaExecStepExecutor.class, executor);
    }

    @Test
    void resolve_SFTP() {
        StepExecutor executor = registry.get("SFTP").orElse(null);
        assertNotNull(executor);
        assertInstanceOf(SftpStepExecutor.class, executor);
    }

    @Test
    void resolve_ARCHIVE() {
        StepExecutor executor = registry.get("ARCHIVE").orElse(null);
        assertNotNull(executor);
        assertInstanceOf(ArchiveStepExecutor.class, executor);
    }

    @Test
    void resolve_LOG_CLEANUP() {
        StepExecutor executor = registry.get("LOG_CLEANUP").orElse(null);
        assertNotNull(executor);
        assertInstanceOf(LogCleanupStepExecutor.class, executor);
    }

    @Test
    void listAll_returns_schemas_for_all_executors() {
        var schemas = registry.listAll();
        assertTrue(schemas.size() >= 5, "Should have at least 5 registered executors");
    }

    @Test
    void get_unknown_type_returns_empty() {
        assertFalse(registry.get("NONEXISTENT").isPresent());
    }
}
