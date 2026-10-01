package com.aurora.pms.dto.request;

import java.util.UUID;

import com.aurora.pms.model.enums.UserStatus;

import jakarta.validation.constraints.Email;

public record UpdateUserRequest(
		String firstName,
		String lastName,
		@Email String email,
		UUID roleId,
		UserStatus status
) {
}
