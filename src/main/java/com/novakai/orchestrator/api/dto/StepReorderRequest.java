package com.novakai.orchestrator.api.dto;

// @author Siddhant Choudhary

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record StepReorderRequest(
    @NotEmpty List<Long> stepIds
) {}
