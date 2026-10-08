package com.aurora.pms.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateConciergeRequestRequest(
		@NotNull(message = "Booking id is required")
		UUID bookingId,

		@NotBlank(message = "Description is required")
		String description,

		String notes,

		UUID serviceId
) {
	public CreateConciergeRequestRequest(UUID bookingId, String description, String notes) {
		this(bookingId, description, notes, null);
	}
}
