# Booking Module Context

## GitHub Ticket

Issue #12: `[Backend] Implementar modulo de Reservas`

Branch: `feature/bookings`

Target PR base: `develop`

## Scope

Implemented the backend API contract for reservations:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/v1/bookings` | List bookings |
| GET | `/api/v1/bookings/{id}` | Get booking by UUID |
| POST | `/api/v1/bookings` | Create booking |
| PUT | `/api/v1/bookings/{id}` | Update booking |

DELETE remains out of scope.

## Update Semantics

`PUT /api/v1/bookings/{id}` follows the same partial-update convention used by existing modules such as rooms and rates: only non-null fields present in the request are applied.

Because null and omitted values are not distinguished by the current DTO shape, `roomId: null` and `rateId: null` do not clear existing relationships.

## Availability

Room overlap validation is applied only when a concrete `roomId` is assigned.

The active statuses considered unavailable for overlapping dates are:

- `pending`
- `confirmed`
- `checked_in`

Statuses such as `cancelled`, `checked_out` and `no_show` do not block room availability.

## Amounts

Money stays in cents. When a rate is present, `totalAmountCents` is calculated as:

`rate.priceCents * number_of_nights`

When no rate is present, `totalAmountCents` is `0`.

Currency is always `GTQ`.
