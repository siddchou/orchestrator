package com.novakai.orchestrator.notification.spi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of notification channels keyed by type string.
 * Mirrors StepExecutorRegistry: ConcurrentHashMap storage, log-and-continue on duplicate type,
 * Optional return on lookup miss, listAll() for schemas, registeredTypes() for key set.
 */
@Component
@Slf4j
public class NotificationChannelRegistry {

    private final Map<String, NotificationChannel> channelMap = new ConcurrentHashMap<>();
    private final List<ChannelConfigSchema> schemas = Collections.synchronizedList(new ArrayList<>());

    public NotificationChannelRegistry(List<NotificationChannel> channels) {
        for (NotificationChannel c : channels) {
            register(c);
        }
    }

    public void register(NotificationChannel channel) {
        String type = channel.getType();
        NotificationChannel previous = this.channelMap.put(type, channel);
        if (previous != null) {
            log.warn("Duplicate notification channel for type '{}': {} replaces {}",
                type, channel.getClass().getSimpleName(), previous.getClass().getSimpleName());
        }
        this.schemas.add(channel.getConfigSchema());
    }

    public Optional<NotificationChannel> get(String type) {
        NotificationChannel channel = channelMap.get(type);
        if (channel == null) {
            log.debug("No notification channel registered for type: {}", type);
            return Optional.empty();
        }
        return Optional.of(channel);
    }

    public List<ChannelConfigSchema> listAll() {
        return new ArrayList<>(schemas);
    }

    public Set<String> registeredTypes() {
        return Collections.unmodifiableSet(channelMap.keySet());
    }
}
