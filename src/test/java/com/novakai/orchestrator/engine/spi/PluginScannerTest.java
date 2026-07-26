package com.novakai.orchestrator.engine.spi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PluginScannerTest {

    @TempDir
    Path tempDir;

    private PluginScanner scanner;

    @AfterEach
    void afterEach() {
        if (scanner != null) {
            scanner.close();
        }
    }

    /** Scanner skips gracefully when plugin directory does not exist. */
    @Test
    void scan_skips_when_directory_missing() {
        var registry = new StepExecutorRegistry(java.util.List.of());
        scanner = new PluginScanner("/nonexistent/path/to/plugins", registry);

        int initialCount = registry.registeredTypes().size();
        scanner.scan();
        assertEquals(initialCount, registry.registeredTypes().size(),
            "No executors should be loaded from a missing directory");
    }

    /** Scanner skips gracefully when plugin directory is empty. */
    @Test
    void scan_skips_when_directory_empty() {
        var registry = new StepExecutorRegistry(java.util.List.of());
        scanner = new PluginScanner(tempDir.toString(), registry);

        scanner.scan();
        assertTrue(registry.registeredTypes().isEmpty(),
            "No executors should be loaded from an empty directory");
    }

    /** Scanner ignores non-JAR files in plugin directory. */
    @Test
    void scan_ignores_non_jar_files() throws Exception {
        var registry = new StepExecutorRegistry(java.util.List.of());
        scanner = new PluginScanner(tempDir.toString(), registry);

        java.nio.file.Files.writeString(tempDir.resolve("readme.txt"), "not a jar");

        scanner.scan();
        assertTrue(registry.registeredTypes().isEmpty(),
            "Non-JAR files should be ignored");
    }

    /** Scanner handles a JAR with ServiceLoader config pointing to a non-existent class gracefully. */
    @Test
    void scan_handles_jar_with_missing_class() throws Exception {
        var registry = new StepExecutorRegistry(java.util.List.of());

        Path jarPath = tempDir.resolve("test-plugin.jar");
        createPluginJarWithMissingClass(jarPath);

        scanner = new PluginScanner(tempDir.toString(), registry);
        assertDoesNotThrow(scanner::scan,
            "Scanner should not crash when a plugin JAR references a missing class");
    }

    /** Scanner can be closed without error. */
    @Test
    void close_does_not_throw() {
        var registry = new StepExecutorRegistry(java.util.List.of());
        scanner = new PluginScanner(tempDir.toString(), registry);

        assertDoesNotThrow(scanner::close, "Close should not throw on empty classloader list");
    }

    /** Helper: create a minimal plugin JAR with META-INF/services entry pointing to non-existent class. */
    private void createPluginJarWithMissingClass(Path jarPath) throws Exception {
        try (var fos = new FileOutputStream(jarPath.toFile());
             var jos = new JarOutputStream(fos)) {

            var servicesDir = new JarEntry("META-INF/services/");
            jos.putNextEntry(servicesDir);
            jos.closeEntry();

            var serviceFile = new JarEntry(
                "META-INF/services/com.novakai.orchestrator.engine.spi.StepExecutor");
            jos.putNextEntry(serviceFile);
            jos.write("com.example.NonExistentTestExecutor\n".getBytes());
            jos.closeEntry();
        }
    }
}
