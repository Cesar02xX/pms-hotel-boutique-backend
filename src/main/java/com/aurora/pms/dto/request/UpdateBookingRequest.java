package com.aurora.pms.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import com.aurora.pms.model.enums.BookingStatus;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateBookingRequest(
		UUID guestId,

		UUID roomTypeId,

		UUID roomId,

		UUID rateId,

		LocalDate checkIn,

		LocalDate checkOut,

		@Positive(message = "Adults must be greater than zero")
		Integer adults,

		@PositiveOrZero(message = "Children must not be negative")
		Integer children,

		BookingStatus status,

		String notes
) {
}
