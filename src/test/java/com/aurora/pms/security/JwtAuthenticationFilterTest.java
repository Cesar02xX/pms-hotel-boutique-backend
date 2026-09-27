package com.aurora.pms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

	@Mock
	private JwtService jwtService;

	@Mock
	private CustomUserDetailsService customUserDetailsService;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void validBearerTokenAuthenticatesRequest() throws ServletException, IOException {
		JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, customUserDetailsService);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/private");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain filterChain = new MockFilterChain();
		UserDetails userDetails = userDetails("admin@aurora.test");

		request.addHeader("Authorization", "Bearer valid.jwt");
		when(jwtService.extractUsername("valid.jwt")).thenReturn(userDetails.getUsername());
		when(customUserDetailsService.loadUserByUsername(userDetails.getUsername())).thenReturn(userDetails);
		when(jwtService.isTokenValid("valid.jwt", userDetails)).thenReturn(true);

		filter.doFilter(request, response, filterChain);

		assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
		assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
				.isEqualTo("admin@aurora.test");
	}

	private UserDetails userDetails(String email) {
		return new User(
				email,
				"password",
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
		);
	}
}
