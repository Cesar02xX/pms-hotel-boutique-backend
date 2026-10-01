package com.aurora.pms.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GuestLinkRequest(
		@NotBlank(message = "Link code is required")
		String code
) {
}
