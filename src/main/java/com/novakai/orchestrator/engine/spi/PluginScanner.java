package com.novakai.orchestrator.engine.spi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Scans a configurable directory for plugin JARs and registers any {@link StepExecutor}
 * implementations found via Java's ServiceLoader SPI.
 *
 * Plugin JARs must contain:
 *   META-INF/services/com.novakai.orchestrator.engine.spi.StepExecutor
 * listing the fully-qualified class name of an implementation with a public no-arg constructor.
 *
 * Configured via {@code orchestrator.plugins.dir} (default: {@code plugins}).
 * Disabled when the directory does not exist or is empty.
 */
@Component
@Slf4j
public class PluginScanner {

    private final String pluginsDirPath;
    private final StepExecutorRegistry registry;
    private final List<URLClassLoader> classLoaders = new ArrayList<>();

    @Autowired
    public PluginScanner(Environment environment, StepExecutorRegistry registry) {
        this.pluginsDirPath = environment.getProperty("orchestrator.plugins.dir", "plugins");
        this.registry = registry;
    }

    /** Test-friendly constructor with explicit directory path. */
    PluginScanner(String pluginsDirPath, StepExecutorRegistry registry) {
        this.pluginsDirPath = pluginsDirPath;
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scan() {
        File pluginsDir = new File(pluginsDirPath);

        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            log.info("Plugin directory '{}' does not exist — skipping plugin scan", pluginsDirPath);
            return;
        }

        File[] jars = pluginsDir.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            log.info("No JAR files found in '{}' — skipping plugin scan", pluginsDirPath);
            return;
        }

        log.info("Scanning {} JAR(s) in '{}' for step executor plugins", jars.length, pluginsDir.getAbsolutePath());

        int loaded = 0;
        for (File jar : jars) {
            try {
                loaded += loadJar(jar);
            } catch (Throwable e) {
                log.error("Failed to load plugin JAR {}: {}", jar.getName(), e.getMessage());
            }
        }

        if (loaded > 0) {
            log.info("Loaded {} executor(s) from {} plugin JAR(s)", loaded, jars.length);
        } else {
            log.info("No executors discovered in {} JAR(s)", jars.length);
        }
    }

    private int loadJar(File jar) throws Exception {
        URL url = jar.toURI().toURL();
        URLClassLoader cl = new URLClassLoader(
            new URL[]{url}, Thread.currentThread().getContextClassLoader());
        classLoaders.add(cl);

        ServiceLoader<StepExecutor> loader = ServiceLoader.load(StepExecutor.class, cl);
        int count = 0;
        for (StepExecutor executor : loader) {
            log.info("Registered plugin executor: type='{}', class={}",
                executor.getType(), executor.getClass().getName());
            registry.register(executor);
            count++;
        }
        return count;
    }

    /** Close classloaders on shutdown to release file handles. */
    @jakarta.annotation.PreDestroy
    public void close() {
        for (URLClassLoader cl : classLoaders) {
            try {
                cl.close();
            } catch (Exception e) {
                log.warn("Error closing plugin classloader: {}", e.getMessage());
            }
        }
        classLoaders.clear();
    }
}
