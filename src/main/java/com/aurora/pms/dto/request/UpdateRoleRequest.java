package com.aurora.pms.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
		@Size(max = 120, message = "Name must be 120 characters or fewer")
		String name,

		Boolean active
) {
}
