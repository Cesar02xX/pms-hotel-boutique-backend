# Integration Log

## 2026-09-27 - Guests API

Integrated the Guest backend module from GitHub issue #8.

Added:

- Guest REST controller under `/api/v1/guests`.
- Create/update request DTOs and response DTO.
- Guest mapper.
- Guest service interface and implementation.
- Controller tests for list, get, create, update, 400 validation, 404 missing resources and invalid UUID/body behavior.
- Security coverage in `CatalogSecurityTest` for all Guest endpoints.
- Project agent/context docs for future contributors.

Shared context already in `develop`:

- `GlobalExceptionHandler` maps invalid route argument conversion to 400.
- `GlobalExceptionHandler` maps malformed or invalid request bodies to 400.

Validation notes:

- `firstName` and `lastName` are required.
- Optional `email` must be valid.
- `documentType` remains constrained to the existing `DocumentType` enum.
