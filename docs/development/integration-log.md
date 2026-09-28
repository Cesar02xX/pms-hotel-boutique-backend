# Integration Log

## 2026-09-27 - Booking Companions API

Integrated the Booking Companion backend module from GitHub issue #13.

Added:

- Nested companion REST controller under `/api/v1/bookings/{bookingId}/companions`.
- Create/update request DTOs and response DTO.
- Booking companion mapper.
- Booking companion service interface and implementation.
- Repository lookups for companions by booking and by booking-owned id.
- Controller tests for list, create, update, delete, missing resources, ownership mismatch, capacity limits, adult/child limits, primary guest duplication, invalid UUIDs and invalid request bodies.
- Security coverage in `CatalogSecurityTest` for all companion endpoints.
- Module context notes in `booking-companion-module-context.md`.

Validation notes:

- The primary guest counts as one adult occupant.
- Total occupants include primary guest plus companions.
- Total occupants cannot exceed the booking room type capacity.
- Adult companions cannot exceed `booking.adults - 1`.
- Child companions cannot exceed `booking.children`.
- Companion create/update timestamps are controlled by the backend.

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

## 2026-09-28 - Bookings API

Integrated the Booking backend module from GitHub issue #12.

Added:

- Booking REST controller under `/api/v1/bookings`.
- Create/update request DTOs and response DTO.
- Booking mapper.
- Booking service interface and implementation.
- Booking repository queries for generated-code uniqueness and active room overlap checks.
- Controller tests for list, get, create, update, validation, missing related resources, room/rate compatibility, room overlap and generated server fields.
- Security coverage in `CatalogSecurityTest` for all Booking endpoints.

Important behavior:

- Initial booking status is `pending`.
- Active overlap statuses are `pending`, `confirmed` and `checked_in`.
- Booking updates are partial: only non-null request fields are applied.
- `totalAmountCents` is calculated from rate price and nights when a rate is present; otherwise it is `0`.
