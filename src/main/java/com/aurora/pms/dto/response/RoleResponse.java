package com.aurora.pms.dto.response;

import java.util.List;
import java.util.UUID;

public record RoleResponse(
		UUID id,
		String code,
		String name,
		Boolean active,
		List<String> permissions
) {
}
