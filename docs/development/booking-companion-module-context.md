# Booking Companion Module Context

## Scope

Issue #13 adds the booking companion REST API on top of the existing `Booking` and `BookingCompanion` domain models. It does not add Liquibase changes or alter the Booking REST API.

## Endpoints

All endpoints are protected by the existing JWT security configuration:

- `GET /api/v1/bookings/{bookingId}/companions`
- `POST /api/v1/bookings/{bookingId}/companions`
- `PUT /api/v1/bookings/{bookingId}/companions/{companionId}`
- `DELETE /api/v1/bookings/{bookingId}/companions/{companionId}`

## Business Rules

- The parent booking must exist before listing, creating, updating or deleting companions.
- Updates and deletes verify that the companion belongs to the requested booking.
- The primary booking guest cannot be registered again as a companion.
- The primary booking guest always counts as one adult occupant.
- Total occupants are calculated as the primary guest plus all companions.
- Total occupants cannot exceed `booking.roomType.capacity`.
- Adult companions cannot exceed `booking.adults - 1`.
- Child companions cannot exceed `booking.children`.
- Create and update operations set timestamps from the backend.

## API Shape

The module uses request/response DTOs and does not expose JPA entities directly:

- `CreateBookingCompanionRequest`
- `UpdateBookingCompanionRequest`
- `BookingCompanionResponse`

Errors use the shared exception flow and `ApiErrorResponse`.

## Validation Coverage

Controller tests cover list/create/update/delete, missing booking, missing companion, companion ownership mismatch, capacity limits, adult and child excess, blank names, invalid enum bodies, invalid UUID paths and JWT security coverage.
