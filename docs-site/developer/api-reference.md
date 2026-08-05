# API Reference

This page links to the live Swagger UI and summarizes the authentication flow and response envelope used by all Novakai Orchestrator REST endpoints.

## Swagger UI

The interactive API documentation is available at `/swagger-ui/index.html` on any running instance. All endpoints are annotated with OpenAPI `@Operation`, `@Parameter`, and `@Tag` annotations for complete parameter descriptions and example responses.

## Authentication Flow

Summary of the JWT-based authentication flow: obtaining a token via the login endpoint, including it in the `Authorization: Bearer <token>` header, and token refresh procedures.

## Response Envelope

The standard JSON response structure used by all API endpoints, including success/error envelopes, pagination format, and error code conventions.
