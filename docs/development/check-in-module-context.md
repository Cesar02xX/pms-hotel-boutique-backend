# Check-in Module Context

## Scope

Issue #17 adds the backend check-in flow for an existing booking. It reuses the Booking module instead of creating a parallel reservation module, and it does not introduce schema changes or folio logic.

## Endpoint

The endpoint is protected by the existing JWT security configuration:

- `POST /api/v1/bookings/{bookingId}/check-in`

`CheckInRequest` is intentionally empty for now because the current domain already owns the data needed for check-in: booking dates, assigned room, titular guest and companions.

## Successful Operation

When check-in succeeds:

- `Booking.status` changes to `checked_in`.
- `Room.status` changes to `occupied`.
- Booking and room timestamps are updated by the backend.
- Both changes happen inside the same transaction.

The response includes booking id, updated status, titular guest summary, room id/number, stay dates, declared occupancy, companion count, total occupants and an operation timestamp.

## Validation Rules

- Booking must exist.
- Booking must be `confirmed`.
- Repeated check-in is rejected.
- Booking must have an assigned room.
- Assigned room must exist.
- Room must belong to the booking room type.
- Room must be `available`.
- Housekeeping must be `clean` or `inspected`.
- Current date must be within the booking stay window: `checkIn <= today < checkOut`.
- No other active booking can overlap the same room and dates.
- Titular guest counts as one adult.
- Companion composition must exactly match `booking.adults` and `booking.children`.
- Total occupants cannot exceed room type capacity.

## Folio

Folio creation is intentionally out of scope. If a future folio module needs to open folios during check-in, it should be integrated through its service in a later flow ticket.

## Test Coverage

Controller tests cover valid check-in, missing booking, invalid status, repeated check-in, missing room, incompatible room, unavailable room, invalid stay date window, incomplete companions, capacity excess, atomicity/no partial update, invalid UUID and JWT security coverage.
