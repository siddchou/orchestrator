# Phase 1 — Plugin Loading Design

## Implementation Status

**COMPLETE — Option A (simple variant) implemented.** `PluginScanner.java` exists in `engine.spi/` with:
- `@EventListener(ApplicationReadyEvent)` trigger
- Scans directory from `orchestrator.plugins.dir` property
- Uses Java `ServiceLoader` to find `StepExecutor` implementations from JARs
- Registers discovered executors with `StepExecutorRegistry`

In practice, Spring component scanning already auto-collects `@Component` StepExecutor beans from the classpath — meaning for plugins bundled in the fat JAR or on the classpath, **zero plugin-loading code is needed**. The PluginScanner provides the optional external-JAR path for future use.

## Context

The codebase is a Spring Boot 4.1.0 monolith (single JAR, `spring-boot-maven-plugin` packaging). All executors are currently `@Component` classes in the same classpath. The goal for v1 plugin loading: allow external step types packaged as JARs to be discovered and registered at startup — **without** hot-reload or custom classloader complexity.

---

## Option A — Classpath Extension (Recommended for v1)

External plugin JARs are dropped into a `/plugins` directory next to the application JAR and added to the classpath via JVM argument (`-cp` or `--add-opens`) or Spring Boot's `spring.main.additional-lazy-initialization` / fat-jar layering.

**Mechanism**:
```bash
java -Dplugins.dir=./plugins \
     -jar orchestrator.jar
```

A `@Configuration` class scans the plugins directory at startup, loads each JAR onto a URLClassLoader that delegates to the app's parent classpath, and uses Spring's `ClassPathScanningCandidateComponentProvider` (or simple reflection + `SpringFactoriesLoader`) to find `StepExecutor` implementations. Each discovered executor is registered as a bean via `@Bean` methods on a dynamically-registered `@Configuration`.

**Simpler variant** (no custom classloader): Bundle plugin JARs into the fat JAR at build time, or require the operator to copy them into `lib/` alongside the app. Spring Boot's launch script picks up all JARs in `lib/`. Spring component scanning finds `@Component` executors automatically — **zero code needed** for discovery.

### Pros
| Criterion | Assessment |
|-----------|------------|
| Complexity | Low — uses standard Spring component scanning. The simpler variant needs zero plugin-loading code. |
| Hot-reload | Not supported (explicitly out of scope for v1). Restart required. |
| Spring compatibility | Full — plugins are regular Spring beans, can inject any service (CredentialDecryptionService, JdbcTemplate, RestClient). |
| Security risk | Low — plugins run in the same JVM with full trust. Mitigated by: running the orchestrator as a non-root user, network-level isolation, and code review before deploying plugin JARs. |
| Isolation | None — plugin bugs can crash the entire JVM. Acceptable for internal tooling; not for multi-tenant SaaS. |

### Cons
- No per-plugin lifecycle management (can't unload a plugin without restart).
- Dependency conflicts: if two plugins depend on different versions of the same library, last-one-wins on the shared classpath.
- Requires manual operator action to copy JARs and restart.

---

## Option B — SPI via ServiceLoader with Isolated ClassLoaders

Each plugin JAR contains a `META-INF/services/com.novakai.orchestrator.engine.spi.StepExecutor` file listing the implementation class(es). A custom `PluginManager` component loads each JAR into its own `URLClassLoader` instance, discovers executors via `ServiceLoader.load(StepExecutor.class, classLoader)`, and wraps them in proxy beans that bridge the isolated classloader to the Spring context.

**Mechanism**:
```java
@Component
public class PluginManager implements ApplicationListener<ContextRefreshedEvent> {
    @Value("${orchestrator.plugins.dir:plugins}")
    private Path pluginsDir;

    private final List<URLClassLoader> classLoaders = new ArrayList<>();

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext ctx = event.getApplicationContext();
        DefaultListableBeanFactory factory = (DefaultListableBeanFactory) ctx.getBeanFactory();

        Files.list(pluginsDir).filter(Files::isRegularFile).forEach(jarPath -> {
            URLClassLoader cl = new URLClassLoader(
                new URL[]{jarPath.toUri().toURL()},
                Thread.currentThread().getContextClassLoader()  // parent delegate for Spring classes
            );
            classLoaders.add(cl);

            ServiceLoader<StepExecutor> loader = ServiceLoader.load(StepExecutor.class, cl);
            for (StepExecutor executor : loader) {
                // Register as Spring bean via factory.registerSingleton()
                String beanName = "plugin-executor-" + executor.getType();
                factory.registerSingleton(beanName, executor);
            }
        });
    }

    @PreDestroy
    public void close() {
        classLoaders.forEach(cl -> { try { cl.close(); } catch (IOException ignored) {} });
    }
}
```

### Pros
| Criterion | Assessment |
|-----------|------------|
| Complexity | Medium-High — custom classloader, ServiceLoader wiring, bean registration at runtime. |
| Hot-reload | Still not supported (URLClassLoader doesn't support reload), but the infrastructure is closer to it. |
| Spring compatibility | Partial — plugins can use Spring types from the parent classpath but cannot be `@Autowired` themselves. They receive dependencies via constructor injection from the wrapper, or via a service-locator pattern on StepContext. |
| Security risk | Medium — isolated classloader prevents plugin A from seeing plugin B's classes, but both share the parent (Spring) classpath. A malicious plugin can still access system properties, file system, and network through the parent loader. |
| Isolation | Classloader-level only — no sandboxing, no security manager (removed in Java 17+, irrelevant in Java 21). |

### Cons
- Spring dependency injection doesn't work naturally across classloader boundaries. Plugins cannot `@Autowired` services; they must receive everything through StepContext or a service locator.
- Debugging is harder: stack traces span classloaders, IDE debuggers don't step into plugin code cleanly.
- Java 21's virtual threads and strong encapsulation (`--add-opens` requirements) can conflict with reflective access across classloaders.
- Adds ~200 lines of boilerplate for marginal v1 benefit.

---

## Comparison Table

| Dimension | Option A (Classpath) | Option B (ServiceLoader + URLClassLoader) |
|-----------|---------------------|------------------------------------------|
| Implementation effort | 1 day (docs only; simpler variant needs zero code) | 3–4 days (PluginManager, testing, edge cases) |
| Lines of new code | ~0 (simpler variant) or ~80 (scan + register) | ~200 |
| Spring bean injection in plugins | Full (`@Autowired` works) | Limited (must use StepContext service locator) |
| Per-plugin isolation | None | Classloader boundary only (no security sandbox) |
| Dependency conflict handling | Manual (operator ensures no version clashes) | Better (each plugin has its own classloader for private deps) |
| Hot-reload capable | No | Not without significant additional work |
| Debugging difficulty | Standard Spring debugging | Cross-classloader stack traces, harder to debug |
| Security surface | Same as app code | Slightly better isolation but no real sandboxing on Java 21 |

---

## Recommendation: Option A (Classpath Extension — Simpler Variant)

**Justification against confirmed project state**:

1. **Spring Boot 4.1.0 + Java 21**: The simpler variant of Option A works natively. Plugin JARs dropped into `lib/` or referenced via `-cp` are picked up by Spring's component scanning. No custom classloader code needed — the existing `StepExecutorFactory` (soon: `StepExecutorRegistry`) already auto-collects all `StepExecutor` beans via constructor injection of `List<StepExecutor>`.

2. **Internal tooling context**: The orchestrator appears to be an internal batch-job system (Oracle DB, SFTP to fixed hosts, Java exec of internal classes). The threat model doesn't require untrusted plugin sandboxing — operators control what JARs get deployed.

3. **Existing pattern alignment**: All 5 current executors are `@Component` beans discovered by Spring. Option A extends this exact pattern: a plugin is a JAR with `@Component` classes that implement `StepExecutor`. The operator drops the JAR on the classpath and restarts — same workflow as adding any dependency today.

4. **Future-proofing**: If hot-reload or stronger isolation becomes needed, Option B's infrastructure can be layered on later without changing the executor contract. The SPI (`StepExecutor` interface) is the stable boundary; the loading mechanism is an implementation detail.

**Documentation deliverable** (`../../docs/plugin-development.md`) covers:
- Creating a new Maven/Gradle module with dependency on orchestrator's SPI module (or just the interface JAR).
- Implementing `StepExecutor`, annotating with `@Component`.
- Packaging as a fat or thin JAR.
- Deployment instructions: copy to `lib/` directory, restart app.
- Example: a `HELLO_WORLD` executor that logs "Hello from plugin" and returns success.
