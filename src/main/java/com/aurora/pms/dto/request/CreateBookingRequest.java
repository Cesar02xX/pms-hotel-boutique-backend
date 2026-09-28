package com.aurora.pms.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateBookingRequest(
		@NotNull(message = "Guest id is required")
		UUID guestId,

		@NotNull(message = "Room type id is required")
		UUID roomTypeId,

		UUID roomId,

		UUID rateId,

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

		String notes
) {
}
