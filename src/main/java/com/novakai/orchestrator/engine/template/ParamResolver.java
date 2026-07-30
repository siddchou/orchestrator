package com.novakai.orchestrator.engine.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves ${...} template references in string values.
 *
 * Supported patterns:
 *   ${job.param.X}          — runtime parameters from POST body
 *   ${step.&lt;id&gt;.output.X}  — StepResult.outputs from a completed step
 *   ${env.X}                — system env vars + job-specific env vars
 *
 * Default value syntax: ${job.param.PORT?8080}
 */
@Component
@Slf4j
public class ParamResolver {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final int MAX_RESOLUTION_PASSES = 3;

    /**
     * Resolve all template references in a string value.
     * Returns the original string if it contains no templates.
     */
    public String resolve(String template, ResolutionContext context) {
        if (template == null || !template.contains("${")) {
            return template;
        }

        String result = template;
        for (int pass = 1; pass <= MAX_RESOLUTION_PASSES; pass++) {
            String previous = result;
            result = resolveOnePass(result, context);
            if (result.equals(previous)) break;
            if (pass >= MAX_RESOLUTION_PASSES && result.contains("${")) {
                log.warn("Template still unresolved after {} passes: {}", MAX_RESOLUTION_PASSES, result);
            }
        }
        return result;
    }

    /**
     * Resolve all template references in a config map's string values.
     * Non-string values (numbers, booleans, nested maps) are left as-is.
     */
    public void resolveInPlace(java.util.Map<String, Object> configMap, ResolutionContext context) {
        if (configMap == null) return;
        for (java.util.Map.Entry<String, Object> entry : configMap.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s) {
                entry.setValue(resolve(s, context));
            }
        }
    }

    private String resolveOnePass(String template, ResolutionContext context) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String ref = matcher.group(1);
            Object value = resolveReference(ref, context);
            matcher.appendReplacement(
                result,
                escapeReplacement(value != null ? value.toString() : matcher.group(0))
            );
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Object resolveReference(String ref, ResolutionContext context) {
        // Handle default value syntax: param_name?default_value
        String[] parts = ref.split("\\?", 2);
        String reference = parts[0];
        String defaultValue = parts.length > 1 ? parts[1] : null;

        Object value = lookup(reference, context);

        if (value == null && defaultValue != null) {
            return defaultValue;
        }

        if (value == null) {
            log.warn("Unresolved template reference: ${}", ref);
        }

        return value;
    }

    private Object lookup(String ref, ResolutionContext context) {
        // ${job.param.X}
        if (ref.startsWith("job.param.")) {
            String key = ref.substring("job.param.".length());
            return context.jobParams().get(key);
        }

        // ${step.<id>.output.X}
        if (ref.startsWith("step.") && ref.contains(".output.")) {
            int dotIndex = ref.indexOf(".output.");
            String stepIdStr = ref.substring(5, dotIndex);
            String outputKey = ref.substring(dotIndex + ".output.".length());

            try {
                Long stepId = Long.parseLong(stepIdStr);
                // Check string-keyed map first, then try to find by numeric ID
                Map<String, Object> outputs = context.stepOutputs().get(stepIdStr);
                if (outputs == null) {
                    // Try finding by numeric key in the map values
                    for (java.util.Map.Entry<String, Map<String, Object>> entry : context.stepOutputs().entrySet()) {
                        try {
                            if (Long.valueOf(entry.getKey()).equals(stepId)) {
                                outputs = entry.getValue();
                                break;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
                return outputs != null ? outputs.get(outputKey) : null;
            } catch (NumberFormatException e) {
                log.warn("Invalid step ID in template reference: {}", ref);
                return null;
            }
        }

        // ${env.X}
        if (ref.startsWith("env.")) {
            String key = ref.substring("env.".length());
            return context.envVars().get(key);
        }

        log.warn("Unknown template reference pattern: {}", ref);
        return null;
    }

    /** Escape $ and \ for Matcher.appendReplacement — required by Java regex. */
    private static String escapeReplacement(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("$", "\\$");
    }
}
