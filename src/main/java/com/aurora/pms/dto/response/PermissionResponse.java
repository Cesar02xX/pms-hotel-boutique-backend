package com.aurora.pms.dto.response;

import java.util.UUID;

public record PermissionResponse(
		UUID id,
		String key,
		String name,
		String description
) {
}
