package com.novakai.orchestrator.engine.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

class ObservabilityTaskDecoratorTest {

    @Test
    void mdc_not_leaked_between_tasks_on_same_thread() {
        var decorator = new ObservabilityTaskDecorator();

        Runnable first = () -> MDC.put("test", "1");
        Runnable second = () -> {
            assertNull(MDC.get("test"), "MDC from previous task should be cleared");
            MDC.put("test", "2");
        };

        decorator.decorate(first).run();
        decorator.decorate(second).run();
    }

    @Test
    void mdc_cleared_after_exception_in_runnable() {
        var decorator = new ObservabilityTaskDecorator();

        Runnable throwsException = () -> {
            MDC.put("key", "value");
            throw new RuntimeException("boom");
        };

        assertThrows(RuntimeException.class, () -> decorator.decorate(throwsException).run());

        // After exception, next task should see clean MDC
        Runnable after = () -> assertNull(MDC.get("key"));
        decorator.decorate(after).run();
    }
}
