package com.aurora.pms.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CancelBookingRequest(
		@NotBlank(message = "Cancellation reason is required")
		String reason
) {
}
