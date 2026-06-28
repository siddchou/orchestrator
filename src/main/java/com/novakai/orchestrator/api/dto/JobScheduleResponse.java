package com.novakai.orchestrator.api.dto;

import com.novakai.orchestrator.domain.entity.JobSchedule;
import java.time.LocalDateTime;

public record JobScheduleResponse(
    Long scheduleId,
    String cronExpression,
    boolean enabled,
    LocalDateTime nextFireTime
) {}
