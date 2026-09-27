package com.aurora.pms.service.impl;

import com.aurora.pms.dto.request.LoginRequest;
import com.aurora.pms.dto.response.AuthResponse;
import com.aurora.pms.model.User;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.security.JwtService;
import com.aurora.pms.security.RefreshTokenService;
import com.aurora.pms.service.AuthService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

	private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

	private final org.springframework.security.authentication.AuthenticationManager authenticationManager;
	private final UserRepository userRepository;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;

	public AuthServiceImpl(
			org.springframework.security.authentication.AuthenticationManager authenticationManager,
			UserRepository userRepository,
			JwtService jwtService,
			RefreshTokenService refreshTokenService
	) {
		this.authenticationManager = authenticationManager;
		this.userRepository = userRepository;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
	}

	@Override
	@Transactional
	public AuthResponse login(LoginRequest request) {
		Authentication authentication = authenticate(request);
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));
		UserDetails userDetails = (UserDetails) authentication.getPrincipal();

		String accessToken = jwtService.generateAccessToken(userDetails);
		String refreshToken = refreshTokenService.createRefreshToken(user);

		return new AuthResponse(
				accessToken,
				refreshToken,
				"Bearer",
				jwtService.getAccessExpirationSeconds()
		);
	}

	private Authentication authenticate(LoginRequest request) {
		try {
			return authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(request.email(), request.password())
			);
		} catch (AuthenticationException exception) {
			throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
		}
	}
}
