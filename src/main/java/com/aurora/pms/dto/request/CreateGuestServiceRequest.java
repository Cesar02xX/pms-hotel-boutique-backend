package com.aurora.pms.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateGuestServiceRequest(
		@NotBlank(message = "Description is required")
		String description,

		String notes
) {
}
