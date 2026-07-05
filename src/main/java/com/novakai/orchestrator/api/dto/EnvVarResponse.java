package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

import com.novakai.orchestrator.domain.entity.JobEnvVar;
import java.util.List;

public record EnvVarResponse(
    Long envVarId,
    String key,
    String value,
    boolean isGlobal
) {}
