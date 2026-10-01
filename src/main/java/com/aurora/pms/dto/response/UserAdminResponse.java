package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.UserStatus;

public record UserAdminResponse(
		UUID id,
		String firstName,
		String lastName,
		String email,
		UUID roleId,
		String roleCode,
		UserStatus status,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
