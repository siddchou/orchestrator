package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.entity.AuditLog;
import com.novakai.orchestrator.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditAspectTest {

    @Autowired
    private AuditLogRepository auditRepo;

    @BeforeEach
    void setUp() {
        auditRepo.deleteAll();
    }

    @Test
    void audit_repo_saves_and_retrieves() {
        AuditLog log = AuditLog.builder()
                .username("admin")
                .action("CREATE_JOB")
                .entityType("JOB")
                .entityId(1L)
                .build();
        auditRepo.save(log);

        var all = auditRepo.findAll();
        assertFalse(all.isEmpty());
        assertEquals("CREATE_JOB", all.get(0).getAction());
        assertEquals("JOB", all.get(0).getEntityType());
        assertEquals(1L, all.get(0).getEntityId());
    }
}
