package com.novakai.orchestrator.repository;

import com.novakai.orchestrator.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByUsernameOrderByCreatedAtDesc(String username);
    List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId);
}
