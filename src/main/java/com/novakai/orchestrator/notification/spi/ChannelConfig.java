package com.novakai.orchestrator.notification.spi;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record ChannelConfig(Map<String, Object> params) {
    public ChannelConfig {
        if (params == null) params = Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public String getString(String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public List<String> getList(String key) {
        Object val = params.get(key);
        if (val instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return Collections.emptyList();
    }
}
