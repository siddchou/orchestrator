package com.novakai.orchestrator.api.controller;

import com.novakai.orchestrator.domain.entity.AuditLog;
import com.novakai.orchestrator.repository.AuditLogRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuditControllerTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @Autowired
    private AuditLogRepository auditRepo;

    String base() { return "http://localhost:" + port; }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        auditRepo.deleteAll();
    }

    @Test
    void list_audit_logs_empty() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/audit", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void list_audit_logs_returns_entries() {
        AuditLog log = AuditLog.builder()
                .username("admin")
                .action("CREATE_JOB")
                .entityType("JOB")
                .entityId(1L)
                .build();
        auditRepo.save(log);

        ResponseEntity<String> response = restTemplate.getForEntity(
                base() + "/api/audit", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("CREATE_JOB"));
        assertTrue(response.getBody().contains("admin"));
    }
}
