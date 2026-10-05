package com.aurora.pms.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
		@NotBlank(message = "Code is required")
		@Size(max = 80, message = "Code must be 80 characters or fewer")
		String code,

		@NotBlank(message = "Name is required")
		@Size(max = 120, message = "Name must be 120 characters or fewer")
		String name,

		Boolean active,

		@NotNull(message = "Permissions are required")
		List<@NotBlank(message = "Permission key is required") String> permissions
) {
}
