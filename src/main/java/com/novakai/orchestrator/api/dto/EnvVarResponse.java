package com.novakai.orchestrator.api.dto;

import com.novakai.orchestrator.domain.entity.JobEnvVar;
import java.util.List;

public record EnvVarResponse(
    Long envVarId,
    String key,
    String value,
    boolean isGlobal
) {}
