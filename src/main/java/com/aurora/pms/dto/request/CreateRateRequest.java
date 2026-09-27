package com.aurora.pms.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateRateRequest(
		@NotNull(message = "Room type id is required")
		UUID roomTypeId,

		@NotBlank(message = "Name is required")
		String name,

		@NotNull(message = "Valid from is required")
		LocalDate validFrom,

		LocalDate validTo,

		@NotNull(message = "Price is required")
		@PositiveOrZero(message = "Price must not be negative")
		Long priceCents,

		@Pattern(regexp = "GTQ", message = "Currency must be GTQ")
		String currency,

		@NotNull(message = "Minimum nights is required")
		@Positive(message = "Minimum nights must be greater than zero")
		Integer minimumNights,

		Boolean refundable,

		Boolean active
) {
}
