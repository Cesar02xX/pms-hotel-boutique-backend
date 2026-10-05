package com.aurora.pms.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Reserva creada desde la web pública. La tarifa, la habitación, el estado y
 * los importes los decide el backend: no forman parte del contrato.
 */
public record PublicCreateBookingRequest(
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
		String notes,

		@NotNull(message = "Guest is required")
		@Valid
		PublicGuestRequest guest
) {
}
