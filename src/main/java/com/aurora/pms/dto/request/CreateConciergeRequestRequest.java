package com.aurora.pms.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateConciergeRequestRequest(
		@NotNull(message = "Booking id is required")
		UUID bookingId,

		@NotBlank(message = "Description is required")
		String description,

		String notes
) {
}
