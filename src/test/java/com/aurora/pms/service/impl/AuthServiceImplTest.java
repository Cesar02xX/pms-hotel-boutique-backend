package com.aurora.pms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aurora.pms.dto.request.LoginRequest;
import com.aurora.pms.dto.response.AuthResponse;
import com.aurora.pms.model.User;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.security.JwtService;
import com.aurora.pms.security.RefreshTokenService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private UserRepository userRepository;

	@Mock
	private JwtService jwtService;

	@Mock
	private RefreshTokenService refreshTokenService;

	@Test
	void validLoginGeneratesAuthResponse() {
		AuthServiceImpl authService = authService();
		LoginRequest request = new LoginRequest("admin@aurora.test", "secret");
		UserDetails userDetails = userDetails(request.email());
		User user = new User();

		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenReturn(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
		when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
		when(jwtService.generateAccessToken(userDetails)).thenReturn("access.jwt");
		when(jwtService.getAccessExpirationSeconds()).thenReturn(1800L);
		when(refreshTokenService.createRefreshToken(user)).thenReturn("refresh-token");

		AuthResponse response = authService.login(request);

		assertThat(response.accessToken()).isEqualTo("access.jwt");
		assertThat(response.refreshToken()).isEqualTo("refresh-token");
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresIn()).isEqualTo(1800L);
	}

	@Test
	void invalidCredentialsThrowGenericAuthenticationException() {
		AuthServiceImpl authService = authService();
		LoginRequest request = new LoginRequest("missing@aurora.test", "wrong");

		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenThrow(new BadCredentialsException("bad credentials"));

		assertThatThrownBy(() -> authService.login(request))
				.isInstanceOf(BadCredentialsException.class)
				.hasMessage("Invalid email or password");
		verify(jwtService, never()).generateAccessToken(any());
		verify(refreshTokenService, never()).createRefreshToken(any());
	}

	@Test
	void inactiveUserCannotAuthenticate() {
		AuthServiceImpl authService = authService();
		LoginRequest request = new LoginRequest("inactive@aurora.test", "secret");

		when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenThrow(new DisabledException("disabled"));

		assertThatThrownBy(() -> authService.login(request))
				.isInstanceOf(BadCredentialsException.class)
				.hasMessage("Invalid email or password");
		verify(jwtService, never()).generateAccessToken(any());
		verify(refreshTokenService, never()).createRefreshToken(any());
	}

	private AuthServiceImpl authService() {
		return new AuthServiceImpl(authenticationManager, userRepository, jwtService, refreshTokenService);
	}

	private UserDetails userDetails(String email) {
		return new org.springframework.security.core.userdetails.User(
				email,
				"password",
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
		);
	}
}
