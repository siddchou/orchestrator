package com.novakai.orchestrator.engine.spi;

import java.util.List;

/**
 * Describes a single field in a step configuration schema.
 * Consumed by Phase 2's dynamic form generator and by config validation at runtime.
 */
public record FieldDefinition(
    String name,                    // JSON key: "url", "method", "credentialRef"
    String label,                   // UI display label: "URL", "HTTP Method"
    FieldType type,                 // see enum below
    boolean required,               // must be present in config JSON
    Object defaultValue,            // null if no default; used by form generator for initial value
    List<String> enumValues,        // non-null only when type == ENUM; drives <select> options
    String helpText                 // inline tooltip / description in UI
) {
    public FieldDefinition {
        if (type == FieldType.ENUM && (enumValues == null || enumValues.isEmpty())) {
            throw new IllegalArgumentException("ENUM fields must provide enumValues");
        }
        if (type != FieldType.ENUM && enumValues != null && !enumValues.isEmpty()) {
            throw new IllegalArgumentException("enumValues only valid for ENUM type");
        }
    }
}
