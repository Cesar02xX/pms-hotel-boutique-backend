package com.aurora.pms.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateRateRequest(
		UUID roomTypeId,

		@Pattern(regexp = ".*\\S.*", message = "Name must not be blank")
		String name,

		LocalDate validFrom,

		LocalDate validTo,

		@PositiveOrZero(message = "Price must not be negative")
		Long priceCents,

		@Pattern(regexp = "GTQ", message = "Currency must be GTQ")
		String currency,

		@Positive(message = "Minimum nights must be greater than zero")
		Integer minimumNights,

		Boolean refundable,

		Boolean active
) {
}
