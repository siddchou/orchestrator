# Contributing to Novakai Orchestrator

## Build Instructions

```bash
# Clone and build
git clone <repo-url>
cd orchestrator
mvn clean install -f orchestrator-parent/pom.xml

# Run tests
mvn verify -f orchestrator-parent/pom.xml
```

## Adding a New Step Type

1. Implement `StepExecutor` interface in `src/main/java/com/novakai/orchestrator/infrastructure/stepexecutors/`
2. Define config schema and validation logic
3. Register as a Spring bean — the registry auto-discovers implementations
4. See [Plugin Development](docs-site/developer/plugin-development.md) for details

## Running Tests

```bash
# Backend tests (H2 test profile)
mvn verify -f orchestrator-parent/pom.xml

# Frontend tests
cd orchestrator-ui && ng test --watch=false
```

## PR Checklist

- [ ] All backend tests pass (`mvn verify`)
- [ ] Frontend builds without errors (`ng build` in `orchestrator-ui/`)
- [ ] Code follows existing conventions (no new abstractions for simple cases)
- [ ] Documentation updated if behavior changes
- [ ] No secrets committed (check `.gitignore`, no API keys in test data)

## Coding Standards

- Java 21 records for DTOs, Lombok `@Builder` for entities
- Package-private visibility by default; public only when needed across modules
- One responsibility per class/method — extract helpers for complex logic
- No comments unless the WHY is non-obvious (hidden constraint, workaround, subtle invariant)
