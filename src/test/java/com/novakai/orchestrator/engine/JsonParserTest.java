package com.novakai.orchestrator.engine;

import com.novakai.orchestrator.domain.config.EnvSetupConfig;
import com.novakai.orchestrator.domain.config.JavaExecConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonParserTest {

    private final JsonParser parser = new JsonParser();

    @Test
    void parse_returns_object_for_valid_json() {
        String json = """
                {"mainClass":"com.Example","jarPath":null,"args":["--run"],"jvmArgs":null,"timeoutMinutes":5}
                """;

        JavaExecConfig config = parser.parse(json, JavaExecConfig.class);

        assertNotNull(config);
        assertEquals("com.Example", config.mainClass());
        assertEquals(5, config.timeoutMinutes());
        assertEquals(List.of("--run"), config.args());
    }

    @Test
    void parse_returns_env_setup_config() {
        String json = """
                {"javaHome":"/usr/lib/jvm/java-21","classpathEntries":["lib/a.jar","lib/b.jar"],"extraEnvVars":{"KEY":"val"}}
                """;

        EnvSetupConfig config = parser.parse(json, EnvSetupConfig.class);

        assertNotNull(config);
        assertEquals("/usr/lib/jvm/java-21", config.javaHome());
        assertEquals(2, config.classpathEntries().size());
        assertEquals("val", config.extraEnvVars().get("KEY"));
    }

    @Test
    void parse_returns_null_for_null_input() {
        assertNull(parser.parse(null, JavaExecConfig.class));
    }

    @Test
    void parse_returns_null_for_blank_input() {
        assertNull(parser.parse("   ", JavaExecConfig.class));
    }

    @Test
    void parse_returns_null_for_empty_string() {
        assertNull(parser.parse("", JavaExecConfig.class));
    }

    @Test
    void parse_throws_for_invalid_json() {
        assertThrows(RuntimeException.class, () -> parser.parse("{invalid}", JavaExecConfig.class));
    }

    @Test
    void parse_handles_nested_map() {
        String json = """
                {"javaHome":"/java","classpathEntries":[],"extraEnvVars":{"A":"1","B":"2"}}
                """;

        EnvSetupConfig config = parser.parse(json, EnvSetupConfig.class);

        assertEquals(2, config.extraEnvVars().size());
        assertEquals("1", config.extraEnvVars().get("A"));
        assertEquals("2", config.extraEnvVars().get("B"));
    }
}
