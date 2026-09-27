package com.aurora.pms.dto.response;

public record AuthResponse(
		String accessToken,
		String refreshToken,
		String tokenType,
		long expiresIn
) {
}
