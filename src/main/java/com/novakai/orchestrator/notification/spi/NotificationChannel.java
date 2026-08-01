package com.novakai.orchestrator.notification.spi;

/**
 * Pluggable notification delivery channel.
 * Mirrors StepExecutor SPI: getType() identifies the channel, send() delivers.
 */
public interface NotificationChannel {
    String getType();

    void send(NotificationEvent event, ChannelConfig config) throws NotificationException;

    ChannelConfigSchema getConfigSchema();
}
