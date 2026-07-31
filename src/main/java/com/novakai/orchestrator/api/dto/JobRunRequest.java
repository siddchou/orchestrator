package com.novakai.orchestrator.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Optional request body for POST /api/jobs/{id}/run.
 * Carries runtime parameters that ParamResolver substitutes in step config templates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRunRequest {

    /** Runtime parameters available as ${job.param.<key>} in step templates. */
    @Builder.Default
    private Map<String, Object> parameters = Map.of();
}
