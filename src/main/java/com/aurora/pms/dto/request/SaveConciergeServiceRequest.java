package com.aurora.pms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveConciergeServiceRequest(
		@NotBlank(message = "Service name is required")
		@Size(max = 120, message = "Service name must not exceed 120 characters")
		String name,

		@Size(max = 1000, message = "Description must not exceed 1000 characters")
		String description,

		Boolean active
) {
}
