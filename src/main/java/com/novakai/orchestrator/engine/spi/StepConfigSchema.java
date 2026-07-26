package com.novakai.orchestrator.engine.spi;

import java.util.List;

/**
 * Machine-readable schema for a step type's configuration fields.
 * Drives UI form generation (Phase 2) and runtime config validation.
 */
public record StepConfigSchema(
    String stepType,              // e.g. "HTTP_CALL" — matches StepExecutor.getType()
    String displayName,           // e.g. "HTTP Call" — shown in palette
    List<FieldDefinition> fields  // ordered list; UI renders fields in this order
) {}
