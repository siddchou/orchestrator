# Phase 1 — Plugin Loading Design

## Implementation Status

**COMPLETE — Option A (simple variant) implemented.** `PluginScanner.java` exists in `engine.spi/` with:

| Feature | Implemented | Detail |
|---------|-------------|--------|
| JAR scanning on startup | ✅ | Scans directory from `orchestrator.plugins.dir` property |
| Per-JAR classloader isolation | ✅ | One `URLClassLoader` per JAR file |
| ServiceLoader-based discovery | ✅ | `ServiceLoader.load(classloader, StepExecutor.class)` |
| Registration into existing registry | ✅ | Loaded executors added to `StepExecutorRegistry` ConcurrentHashMap |
| Lifecycle management | ✅ | Classloaders closed on `@PreDestroy` |
| Timing | ✅ | Runs on `ApplicationReadyEvent` (after all Spring beans initialized) |
| Duplicate detection | ✅ | Registry's `putIfAbsent` + warning log |

## Two Architectural Options

### Option A — Java ServiceLoader + URLClassLoader *(IMPLEMENTED)*

**How it works:**
1. On `ApplicationReadyEvent`, scan a configurable directory for `.jar` files
2. For each JAR, create a `URLClassLoader(urls, parentClassLoader)`
3. Use `ServiceLoader.load(classloader, StepExecutor.class)` to discover implementations
4. Register each discovered executor into the existing `StepExecutorRegistry`

**Plugin JAR structure:**
```
my-plugin.jar
├── com/example/MyStepExecutor.class      (implements engine.spi.StepExecutor)
└── META-INF/services/
    └── com.novakai.orchestrator.engine.spi.StepExecutor  (contains: com.example.MyStepExecutor)
```

**Advantages:**
- Standard Java SPI — no custom manifest parsing, no classpath hacking
- Per-JAR isolation — each plugin has its own classloader, preventing transitive dependency conflicts
- No Spring context manipulation — plugins are plain Java objects registered into the registry map
- Simple to test — `PluginScannerTest` can place JARs in a temp directory and verify loading

**Disadvantages:**
- Requires `META-INF/services` manifest entry in each plugin JAR
- No hot-reload — plugins loaded once at startup, require restart to update
- Parent classloader delegation means plugin must depend on orchestrator's SPI jar as a compile-time dependency (no fully isolated plugin with its own copy of the interface)

**Configuration:**
```yaml
orchestrator:
  plugins:
    dir: /opt/orchestrator/plugins   # empty = skip scanning
```

### Option B — Spring @ComponentScan + Plugin ApplicationContext *(NOT IMPLEMENTED)*

**How it would work:**
1. Create a child `GenericApplicationContext` per plugin JAR
2. Set its classloader to a `URLClassLoader` for the JAR
3. Run `ClassPathScanningCandidateComponentProvider` for `@Component` beans within that context
4. Import discovered `StepExecutor` beans into the registry

**Advantages:**
- Plugins can use Spring annotations (`@Value`, `@Autowired`) — more flexible configuration
- No `META-INF/services` manifest needed — standard Spring component scanning
- Can share beans from parent context (e.g., `JdbcTemplate`, `RestTemplate`) if desired

**Disadvantages:**
- More complex lifecycle management — child contexts must be refreshed and closed properly
- Bean name collisions between plugins and main app require careful naming conventions
- Tighter coupling to Spring internals
- Harder to test — requires mocking Spring context creation

## Recommendation

**Option A is the right choice for Phase 1** and is what's implemented. It provides:
- Simplicity — standard Java SPI, no framework-specific plugin machinery
- Isolation — per-JAR classloaders prevent dependency conflicts
- Testability — easy to test with temp directories and mock JARs

Option B can be evaluated in a future phase if plugins need Spring bean injection (e.g., accessing `JdbcTemplate` without passing it through the executor constructor). The current `DbQueryStepExecutor` resolves datasources via config reference, not direct injection, which avoids this need.

## Plugin Development Guide

A minimal plugin JAR requires:

1. **Compile against** the orchestrator SPI jar (for `StepExecutor`, `StepContext`, `StepResult` interfaces)
2. **Implement** `StepExecutor`:
```java
package com.example;

public class HelloStepExecutor implements StepExecutor {
    @Override public String getType() { return "HELLO"; }
    @Override public StepConfigSchema getConfigSchema() { ... }
    @Override public StepResult execute(StepContext ctx) {
        ctx.getLogSink().write("Hello, World!");
        return StepResult.success(Map.of(), "Greeted", Duration.ZERO);
    }
}
```
3. **Create** `META-INF/services/com.novakai.orchestrator.engine.spi.StepExecutor` containing:
```
com.example.HelloStepExecutor
```
4. **Drop JAR** into the configured plugins directory
5. **Restart** the orchestrator

See `../../docs/plugin-development.md` for a complete guide with build instructions, troubleshooting, and API reference.

## Security Considerations

| Concern | Status | Mitigation |
|---------|--------|------------|
| Arbitrary code execution | Inherent risk | Plugins directory should be restricted to trusted JARs only; file system permissions on the plugins directory |
| Classloader memory leak | Addressed | `@PreDestroy` closes all classloaders; consider adding a plugin unload API for future hot-reload |
| Dependency conflicts | Mitigated | Per-JAR classloader isolation prevents transitive dependency clashes between plugins |
| No sandboxing | Known limitation | Phase 1 does not sandbox plugin execution — a malicious executor can access the file system, network, etc. via the application's JVM permissions |
