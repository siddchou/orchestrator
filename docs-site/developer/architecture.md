# Architecture

This page documents the project structure, key interfaces and classes, and the execution flow of Novakai Orchestrator.

## Package Layout

Overview of the Maven multi-module structure (`orchestrator-parent`, `orchestrator-cli`, etc.) and the Java package organization within each module.

## Key Classes

Descriptions of core components:
- **StepExecutorRegistry** — Discovers and manages step executor implementations.
- **JobExecutionOrchestrator** — Coordinates the lifecycle of a job run from start to finish.
- **JwtService** — Handles token generation, validation, and refresh for API authentication.
- **AuditLog** — Records all auditable actions for compliance and debugging.

## Execution Flow Diagram

A sequence diagram showing how a job run flows from the trigger request through step dispatch, execution, logging, and final status update.
