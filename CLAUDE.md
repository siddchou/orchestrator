# Project Instructions

## Graphify Knowledge Graph

This project uses [Graphify](https://github.com/Graphify-Labs/graphify) for codebase exploration. The graph is stored in `graphify-out/` (gitignored).

**Before making changes to the codebase, always:**

1. **Update the graph if stale** — check `graphify-out/.graphify_root` against current commit:
   ```bash
   uv tool run --from graphifyy graphify update . --code-only 2>&1
   ```

2. **Query the graph to understand impact** before editing core components:
   ```bash
   # Find what depends on a class you're about to change
   uv tool run --from graphifyy graphify query "what connects [ClassName] to?" 2>&1

   # Trace paths between components
   uv tool run --from graphifyy graphify path "[ComponentA]" "[ComponentB]" 2>&1

   # Explain a class's role in the system
   uv tool run --from graphifyy graphify explain "[ClassName]" 2>&1
   ```

3. **Read `graphify-out/GRAPH_REPORT.md`** for an overview of communities and hubs before architectural changes.

This is especially important when touching core modules: `StepExecutorRegistry`, `JobExecutionOrchestrator`, `JwtService`, `AuditLog`, or any shared service used across multiple subsystems.
