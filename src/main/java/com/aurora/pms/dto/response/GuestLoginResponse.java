package com.aurora.pms.dto.response;

public record GuestLoginResponse(
		String accessToken,
		String tokenType,
		long expiresIn
) {
}
