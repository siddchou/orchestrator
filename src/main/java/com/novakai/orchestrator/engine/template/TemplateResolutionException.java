package com.novakai.orchestrator.engine.template;

/**
 * Thrown when a template reference cannot be resolved in strict mode.
 */
public class TemplateResolutionException extends RuntimeException {
    public TemplateResolutionException(String message) {
        super(message);
    }
}
