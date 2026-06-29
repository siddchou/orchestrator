package com.novakai.orchestrator.api.controller;

import com.novakai.orchestrator.api.dto.ApiResponse;
import com.novakai.orchestrator.domain.entity.AuditLog;
import com.novakai.orchestrator.repository.AuditLogRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditLogRepository auditRepo;

    public AuditController(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @GetMapping
    public ApiResponse<List<AuditLog>> list() {
        return ApiResponse.success(auditRepo.findAll());
    }
}
