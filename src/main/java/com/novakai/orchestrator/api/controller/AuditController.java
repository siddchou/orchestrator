package com.novakai.orchestrator.api.controller;

// @author Siddhant Choudhary

import com.novakai.orchestrator.api.dto.ApiResponse;
import com.novakai.orchestrator.domain.entity.AuditLog;
import com.novakai.orchestrator.repository.AuditLogRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AuditController {

    private final AuditLogRepository auditRepo;

    public AuditController(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @GetMapping
    public ApiResponse<List<AuditLog>> list() {
        log.info("Audit log list requested");
        return ApiResponse.success(auditRepo.findAll());
    }
}
