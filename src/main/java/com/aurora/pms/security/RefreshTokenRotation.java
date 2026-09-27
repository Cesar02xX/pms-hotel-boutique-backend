package com.aurora.pms.security;

import com.aurora.pms.model.User;

public record RefreshTokenRotation(
		User user,
		String refreshToken
) {
}
