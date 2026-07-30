package com.novakai.orchestrator.engine.template;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParamResolverTest {

    private final ParamResolver resolver = new ParamResolver();

    @Test
    void resolvesJobParameter() {
        var ctx = new ResolutionContext(
            Map.of("API_URL", "https://api.example.com"),
            Map.of(),
            Map.of()
        );
        assertEquals("https://api.example.com", resolver.resolve("${job.param.API_URL}", ctx));
    }

    @Test
    void resolvesStepOutput() {
        var ctx = new ResolutionContext(
            Map.of(),
            Map.of("10", Map.of("artifactPath", "/dist/app.jar")),
            Map.of()
        );
        assertEquals("/dist/app.jar", resolver.resolve("${step.10.output.artifactPath}", ctx));
    }

    @Test
    void resolvesEnvVar() {
        var ctx = new ResolutionContext(
            Map.of(),
            Map.of(),
            Map.of("HOME", "/home/user")
        );
        assertEquals("/home/user/work", resolver.resolve("${env.HOME}/work", ctx));
    }

    @Test
    void resolvesMultipleReferences() {
        var ctx = new ResolutionContext(
            Map.of("deployDir", "/opt/deploy"),
            Map.of("5", Map.of("artifact", "/dist/app.jar")),
            Map.of()
        );
        assertEquals("cp /dist/app.jar /opt/deploy",
            resolver.resolve("cp ${step.5.output.artifact} ${job.param.deployDir}", ctx));
    }

    @Test
    void unresolvedJobParameterReturnsEmptyPlaceholder() {
        var ctx = new ResolutionContext(Map.of(), Map.of(), Map.of());
        // Unresolved references log a warning and keep the placeholder
        String result = resolver.resolve("${job.param.MISSING}", ctx);
        assertEquals("${job.param.MISSING}", result);
    }

    @Test
    void defaultValueOnMissingParam() {
        var ctx = new ResolutionContext(Map.of(), Map.of(), Map.of());
        assertEquals("8080", resolver.resolve("${job.param.PORT?8080}", ctx));
    }

    @Test
    void noTemplateMarkersReturnsUnchanged() {
        var ctx = new ResolutionContext(Map.of(), Map.of(), Map.of());
        assertEquals("plain-text-value", resolver.resolve("plain-text-value", ctx));
    }

    @Test
    void nullInputReturnsNull() {
        assertNull(resolver.resolve(null, new ResolutionContext(Map.of(), Map.of(), Map.of())));
    }

    @Test
    void resolveInPlaceUpdatesStringValues() {
        var ctx = new ResolutionContext(
            Map.of("host", "localhost"),
            Map.of(),
            Map.of()
        );
        java.util.Map<String, Object> mutableMap = new java.util.HashMap<>();
        mutableMap.put("url", "${job.param.host}:8080");
        resolver.resolveInPlace(mutableMap, ctx);
        assertEquals("localhost:8080", mutableMap.get("url"));
    }

    @Test
    void stepOutputNumericKeyResolution() {
        // Step outputs are keyed as strings in ResolutionContext
        var ctx = new ResolutionContext(
            Map.of(),
            Map.of("42", Map.of("result", 100)),
            Map.of()
        );
        assertEquals("100", resolver.resolve("${step.42.output.result}", ctx));
    }
}
