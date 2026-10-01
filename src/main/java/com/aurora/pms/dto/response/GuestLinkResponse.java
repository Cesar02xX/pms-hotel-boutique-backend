package com.aurora.pms.dto.response;

public record GuestLinkResponse(
		String accessToken,
		String tokenType,
		long expiresIn
) {
}
