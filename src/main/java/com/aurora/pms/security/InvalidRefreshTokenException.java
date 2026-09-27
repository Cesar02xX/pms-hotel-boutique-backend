package com.aurora.pms.security;

import org.springframework.security.core.AuthenticationException;

public class InvalidRefreshTokenException extends AuthenticationException {

	private static final String MESSAGE = "Invalid or expired refresh token";

	public InvalidRefreshTokenException() {
		super(MESSAGE);
	}
}
