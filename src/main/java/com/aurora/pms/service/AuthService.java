package com.aurora.pms.service;

import com.aurora.pms.dto.request.LoginRequest;
import com.aurora.pms.dto.request.RefreshTokenRequest;
import com.aurora.pms.dto.response.AuthResponse;

public interface AuthService {

	AuthResponse login(LoginRequest request);

	AuthResponse refresh(RefreshTokenRequest request);

	void logout(RefreshTokenRequest request);
}
