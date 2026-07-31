package com.novakai.orchestrator.engine.spi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FieldDefinitionTest {

    @Test
    void enum_field_with_values_builds() {
        FieldDefinition field = new FieldDefinition(
                "method", "HTTP Method", FieldType.ENUM, true, "GET",
                List.of("GET", "POST", "PUT", "DELETE"), "Choose HTTP method"
        );
        assertEquals("method", field.name());
        assertEquals(FieldType.ENUM, field.type());
        assertNotNull(field.enumValues());
    }

    @Test
    void enum_field_without_values_throws() {
        assertThrows(IllegalArgumentException.class, () -> new FieldDefinition(
                "method", "HTTP Method", FieldType.ENUM, true, null,
                List.of(), "Choose HTTP method"
        ));
    }

    @Test
    void non_enum_with_enum_values_throws() {
        assertThrows(IllegalArgumentException.class, () -> new FieldDefinition(
                "url", "URL", FieldType.STRING, true, null,
                List.of("http", "https"), null
        ));
    }

    @Test
    void schema_with_zero_fields_builds() {
        StepConfigSchema schema = new StepConfigSchema("NO_CONFIG", "No Config", List.of());
        assertEquals("NO_CONFIG", schema.stepType());
        assertTrue(schema.fields().isEmpty());
    }

    @Test
    void string_field_without_enum_values_builds() {
        FieldDefinition field = new FieldDefinition(
                "url", "URL", FieldType.STRING, true, null, null, "Enter URL"
        );
        assertEquals(FieldType.STRING, field.type());
        assertNull(field.enumValues());
    }
}
