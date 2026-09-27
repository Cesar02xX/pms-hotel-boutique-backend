# AGENTS

## Project Context

Backend for PMS Hotel Boutique Aurora. The app is a Spring Boot API with PostgreSQL, Liquibase, Spring Security JWT, Swagger/OpenAPI and layered modules:

Controller -> Service -> ServiceImpl -> Repository -> Model -> PostgreSQL.

## Working Rules

- Start feature work from an updated `develop` branch.
- Create feature branches for ticket work and open PRs back to `develop`.
- Keep controllers thin: no business logic and no direct repository access.
- Do not expose JPA entities directly in request or response bodies.
- Keep Liquibase migrations additive and avoid editing already executed migrations unless the team explicitly decides otherwise.
- Keep protected endpoints behind the existing JWT security setup unless a ticket explicitly says otherwise.
- Use the shared `ApiErrorResponse` format through `GlobalExceptionHandler`.

## Test Commands

Windows:

```powershell
.\mvnw.cmd test
```

macOS/Linux:

```bash
./mvnw test
```

## Current Shared Exception Behavior

`GlobalExceptionHandler` already handles invalid path argument conversion and unreadable request bodies as `400 Bad Request` using the shared `ApiErrorResponse` shape:

- `MethodArgumentTypeMismatchException`
- `HttpMessageNotReadableException`

This applies API-wide, including invalid UUID route parameters, malformed JSON, enum values, dates and UUIDs in request bodies.
