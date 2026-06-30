package com.novakai.orchestrator.security;

import com.novakai.orchestrator.domain.entity.AuditLog;
import com.novakai.orchestrator.repository.AuditLogRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Stream;

@Aspect
@Component
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditRepo;

    public AuditAspect(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    @AfterReturning(
            pointcut = "@annotation(auditable)",
            returning = "result"
    )
    public void audit(JoinPoint jp, Auditable auditable, Object result) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";

        // Try to extract entity ID from method arguments
        Long entityId = extractEntityId(jp.getArgs());

        AuditLog entry = AuditLog.builder()
                .username(username)
                .action(auditable.action())
                .entityType(auditable.entityType())
                .entityId(entityId)
                .createdAt(LocalDateTime.now())
                .build();
        auditRepo.save(entry);
        log.debug("Audit: user={} action={} entityType={} entityId={}", username, auditable.action(), auditable.entityType(), entityId);
    }

    private Long extractEntityId(Object[] args) {
        return Arrays.stream(args)
                .filter(arg -> arg != null)
                .filter(arg -> arg instanceof Long || arg instanceof Number)
                .map(arg -> {
                    if (arg instanceof Long) return (Long) arg;
                    if (arg instanceof Number) return ((Number) arg).longValue();
                    return null;
                })
                .filter(id -> id != null && id > 0)
                .findFirst()
                .orElse(null);
    }
}
