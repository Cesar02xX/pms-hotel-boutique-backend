package com.aurora.pms.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.aurora.pms.config.JwtProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class JwtServiceTest {

	private static final String SECRET = "01234567890123456789012345678901";

	@Test
	void validJwtValidatesAndUsesEmailAsSubject() {
		JwtService jwtService = jwtService(1_800_000);
		UserDetails userDetails = userDetails("manager@aurora.test");

		String token = jwtService.generateAccessToken(userDetails);

		assertThat(jwtService.extractUsername(token)).isEqualTo("manager@aurora.test");
		assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
	}

	@Test
	void expiredJwtIsRejected() {
		JwtService jwtService = jwtService(-1_000);
		UserDetails userDetails = userDetails("expired@aurora.test");

		String token = jwtService.generateAccessToken(userDetails);

		assertThat(jwtService.isTokenValid(token, userDetails)).isFalse();
	}

	@Test
	void alteredSignatureIsRejected() {
		JwtService jwtService = jwtService(1_800_000);
		UserDetails userDetails = userDetails("tampered@aurora.test");
		String token = jwtService.generateAccessToken(userDetails);
		String[] tokenParts = token.split("\\.");
		tokenParts[2] = (tokenParts[2].startsWith("a") ? "b" : "a") + tokenParts[2].substring(1);
		String alteredToken = String.join(".", tokenParts);

		assertThat(jwtService.isTokenValid(alteredToken, userDetails)).isFalse();
	}

	private JwtService jwtService(long accessExpiration) {
		JwtProperties properties = new JwtProperties();
		properties.setSecret(SECRET);
		properties.setAccessExpiration(accessExpiration);
		properties.setRefreshExpiration(604_800_000);
		return new JwtService(properties);
	}

	private UserDetails userDetails(String email) {
		return new User(
				email,
				"password",
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
		);
	}
}
