# Packaging

This page covers the Maven build profiles, fat JAR contents, and a Dockerfile template for containerizing Novakai Orchestrator.

## Maven Build Profiles

Description of each Maven profile (`dev`, `prod`, etc.), what dependencies or configurations they activate, and how to invoke them during the build.

## Fat JAR Contents

What is included in the executable JAR produced by `mvn package`, including embedded dependencies, static assets, and configuration files.

## Dockerfile Example

A ready-to-use Dockerfile template for building a container image, including multi-stage build steps, non-root user setup, and health check configuration.
