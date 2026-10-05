package com.aurora.pms.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateRolePermissionsRequest(
		@NotNull(message = "Permissions are required")
		List<@NotBlank(message = "Permission key is required") String> permissions
) {
}
