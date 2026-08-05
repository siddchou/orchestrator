# User Guide Overview

This page explains the core mental model of Novakai Orchestrator: how jobs, steps, and runs relate to one another.

## Jobs, Steps, Runs

A job is a named workflow composed of ordered steps. Each step has a type (e.g., `ENV_SETUP`, `JAVA_EXEC`, `SFTP`) and configuration parameters. A run is a single execution instance of a job, with its own status lifecycle from `PENDING` through completion or failure.

## Execution Model

Description of how the StepExecutorRegistry dispatches steps, how the JobExecutionOrchestrator manages the run lifecycle, and the thread model used for concurrent executions.

## Status Lifecycle

A diagram and table showing all possible run statuses and the transitions between them (e.g., `PENDING` -> `RUNNING` -> `COMPLETED` / `FAILED`).
