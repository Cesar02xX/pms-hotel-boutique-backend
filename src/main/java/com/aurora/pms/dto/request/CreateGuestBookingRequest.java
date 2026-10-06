package com.aurora.pms.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Reserva creada por un huésped autenticado desde su portal.
 * El huésped asociado se obtiene directamente del token (GuestPrincipal).
 */
public record CreateGuestBookingRequest(
		@NotNull(message = "Room type id is required")
		UUID roomTypeId,

		@NotNull(message = "Check-in date is required")
		LocalDate checkIn,

		@NotNull(message = "Check-out date is required")
		LocalDate checkOut,

		@NotNull(message = "Adults is required")
		@Positive(message = "Adults must be greater than zero")
		Integer adults,

		@NotNull(message = "Children is required")
		@PositiveOrZero(message = "Children must not be negative")
		Integer children,

		@Size(max = 1000, message = "Notes must be 1000 characters or fewer")
		String notes
) {
}
