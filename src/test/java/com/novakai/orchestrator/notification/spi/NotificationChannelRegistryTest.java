package com.novakai.orchestrator.notification.spi;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.novakai.orchestrator.engine.spi.FieldDefinition;
import com.novakai.orchestrator.engine.spi.FieldType;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NotificationChannelRegistryTest {

    private static final class MockChannel implements NotificationChannel {
        private final String type;

        MockChannel(String type) { this.type = type; }

        @Override public String getType() { return type; }

        @Override public void send(NotificationEvent event, ChannelConfig config) {}

        @Override public ChannelConfigSchema getConfigSchema() {
            return new ChannelConfigSchema(type, Collections.emptyList());
        }
    }

    @Test
    void register_distinct_types_all_resolve() {
        NotificationChannelRegistry registry = new NotificationChannelRegistry(List.of());
        registry.register(new MockChannel("EMAIL"));
        registry.register(new MockChannel("SLACK_WEBHOOK"));
        registry.register(new MockChannel("WEBHOOK"));

        assertTrue(registry.get("EMAIL").isPresent());
        assertTrue(registry.get("SLACK_WEBHOOK").isPresent());
        assertTrue(registry.get("WEBHOOK").isPresent());
    }

    @Test
    void register_duplicate_type_warns_and_last_wins() {
        Logger logger = (Logger) LoggerFactory.getLogger(NotificationChannelRegistry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);

        NotificationChannelRegistry registry = new NotificationChannelRegistry(List.of());
        MockChannel first = new MockChannel("DUPE");
        MockChannel second = new MockChannel("DUPE");
        registry.register(first);
        registry.register(second);

        assertSame(second, registry.get("DUPE").orElse(null));

        List<ILoggingEvent> events = appender.list;
        assertTrue(events.stream().anyMatch(e -> e.getMessage() != null && e.getMessage().contains("Duplicate notification channel")),
                "Should log duplicate warning");
    }

    @Test
    void get_unregistered_type_returns_empty() {
        NotificationChannelRegistry registry = new NotificationChannelRegistry(List.of());
        assertFalse(registry.get("NONEXISTENT").isPresent());
    }

    @Test
    void listAll_returns_schema_for_every_registered_channel() {
        NotificationChannelRegistry registry = new NotificationChannelRegistry(List.of());
        registry.register(new MockChannel("A"));
        registry.register(new MockChannel("B"));

        List<ChannelConfigSchema> schemas = registry.listAll();
        assertEquals(2, schemas.size());
    }

    @Test
    void registeredTypes_returns_all_type_strings() {
        NotificationChannelRegistry registry = new NotificationChannelRegistry(List.of());
        registry.register(new MockChannel("P"));
        registry.register(new MockChannel("Q"));

        Set<String> types = registry.registeredTypes();
        assertEquals(Set.of("P", "Q"), types);
    }
}
