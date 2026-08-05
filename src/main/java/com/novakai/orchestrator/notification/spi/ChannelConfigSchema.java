package com.novakai.orchestrator.notification.spi;

import com.novakai.orchestrator.engine.spi.FieldDefinition;

import java.util.List;

public record ChannelConfigSchema(
    String type,
    List<FieldDefinition> fields
) {}
