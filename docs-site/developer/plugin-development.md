# Plugin Development

This page explains how to add a new step type to Novakai Orchestrator by implementing the `StepExecutor` interface.

## StepExecutor Interface

The contract that all step types must implement, including method signatures for execution, validation, and configuration parsing.

## Registration

How to register a new step executor with the `StepExecutorRegistry`, including Spring component scanning and bean configuration.

## Configuration Schema

How to define the JSON schema for your step type's configuration, ensuring it validates correctly at job creation time.

## Testing Pattern

The recommended testing approach for step executors, including unit test templates, mock configurations, and integration test patterns.
