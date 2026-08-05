# Systemd Deployment

This page documents how to deploy Novakai Orchestrator as a systemd service on Linux, including OS user setup, the service file, and journal log access.

## OS User Setup

Best practices for creating a dedicated system user to run the orchestrator process, with appropriate file permissions for logs, data, and configuration directories.

## Systemd Service File

A complete `.service` unit file template with restart policies, environment file references, resource limits, and dependency ordering.

## Environment File

How to structure the `.env` file referenced by the systemd service, including which variables must be set and security considerations for credential files.

## Journal Log Access

Commands for viewing orchestrator logs via `journalctl`, including filtering by unit name, following live output, and setting log retention policies.
