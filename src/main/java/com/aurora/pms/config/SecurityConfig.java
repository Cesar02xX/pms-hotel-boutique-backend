package com.aurora.pms.config;

import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.security.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtAuthenticationFilter jwtAuthenticationFilter,
			ObjectMapper objectMapper
	) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.formLogin(AbstractAuthenticationFilterConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.exceptionHandling(exceptionHandling -> exceptionHandling
						.authenticationEntryPoint((request, response, authException) -> {
							HttpStatus status = HttpStatus.UNAUTHORIZED;
							ApiErrorResponse errorResponse = ApiErrorResponse.of(
									status.value(),
									status.getReasonPhrase(),
									"Unauthorized",
									request.getRequestURI()
							);
							response.setStatus(status.value());
							response.setContentType(MediaType.APPLICATION_JSON_VALUE);
							objectMapper.writeValue(response.getOutputStream(), errorResponse);
						}))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/api/v1/health",
								"/api/v1/auth/login",
								"/api/v1/auth/refresh",
								"/api/v1/auth/logout",
								"/swagger-ui/**",
								"/swagger-ui.html",
								"/v3/api-docs/**")
						.permitAll()
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
			throws Exception {
		return authenticationConfiguration.getAuthenticationManager();
	}

	@Bean
	public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
			JwtAuthenticationFilter jwtAuthenticationFilter
	) {
		FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(
				jwtAuthenticationFilter
		);
		registration.setEnabled(false);
		return registration;
	}
}
