package com.novakai.orchestrator.engine.spi;

/**
 * Field types for step configuration schemas.
 * Drives UI form rendering (Phase 2) and runtime presence validation.
 */
public enum FieldType {
    STRING,       // free-form text input
    NUMBER,       // numeric input (int/long/double)
    BOOLEAN,      // checkbox / toggle
    ENUM,         // select dropdown; values from FieldDefinition.enumValues
    SECRET_REF,   // credential reference picker (resolves to JobCredential.credentialRef)
    FILE_PATTERN, // glob pattern input with validation hint
    LIST_STRING   // comma-separated or tag/chip input; stored as JSON array
}
