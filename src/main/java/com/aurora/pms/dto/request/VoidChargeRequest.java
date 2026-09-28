package com.aurora.pms.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VoidChargeRequest(
		@NotBlank(message = "Void reason is required")
		String reason
) {
}
