package com.aurora.pms.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record CreateGuestServiceRequest(
		@NotBlank(message = "Description is required")
		String description,

		String notes,

		UUID serviceId
) {
	public CreateGuestServiceRequest(String description, String notes) {
		this(description, notes, null);
	}
}
