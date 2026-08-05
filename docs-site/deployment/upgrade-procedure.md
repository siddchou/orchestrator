# Upgrade Procedure

This page provides the rolling upgrade steps for Novakai Orchestrator, including pre-upgrade backup and post-upgrade validation.

## Pre-Upgrade Backup

Steps to back up the database, configuration files, and any user-generated data before starting an upgrade.

## Deploy New Version

The sequence: stop the service, deploy the new JAR or container image, apply any database migrations, and start the service.

## Post-Upgrade Validation

Checks to confirm the upgrade succeeded: health endpoint status, database schema version, API responsiveness, and sample job execution.

## Rollback on Failure

Steps to revert to the previous version if the upgrade fails at any stage, including database rollback considerations.
