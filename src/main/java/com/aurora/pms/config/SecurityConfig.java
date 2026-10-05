package com.aurora.pms.config;

import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.security.JwtAuthenticationFilter;
import com.aurora.pms.security.SecurityPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

	private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
	private static final String GUEST_AUTHORITY = "ROLE_GUEST";

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
						.authenticationEntryPoint((request, response, authException) ->
								writeErrorResponse(request, response, objectMapper, HttpStatus.UNAUTHORIZED))
						.accessDeniedHandler((request, response, accessDeniedException) ->
								writeErrorResponse(request, response, objectMapper, HttpStatus.FORBIDDEN)))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/api/v1/health",
								"/api/v1/auth/login",
								"/api/v1/auth/refresh",
								"/api/v1/auth/logout",
								"/api/v1/guest/auth/link",
								"/swagger-ui/**",
								"/swagger-ui.html",
								"/v3/api-docs/**")
						.permitAll()
						// Web pública: solo estas rutas y métodos exactos, nunca /api/v1/public/**.
						.requestMatchers(HttpMethod.GET,
								"/api/v1/public/room-types",
								"/api/v1/public/rates",
								"/api/v1/public/availability")
						.permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/public/bookings")
						.permitAll()
						.requestMatchers("/api/v1/guest/**")
						.hasAuthority(GUEST_AUTHORITY)
						.requestMatchers(HttpMethod.GET, "/api/v1/admin/amenities/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOMS_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/admin/amenities")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOMS_WRITE)
						.requestMatchers(HttpMethod.PUT, "/api/v1/admin/amenities/*")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOMS_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/admin/room-service/products")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOM_SERVICE_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/admin/room-service/products")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOM_SERVICE_WRITE)
						.requestMatchers(HttpMethod.PUT, "/api/v1/admin/room-service/products/*")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOM_SERVICE_WRITE)
						.requestMatchers(HttpMethod.POST, "/api/v1/admin/inventory/items")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.INVENTORY_WRITE)
						.requestMatchers(HttpMethod.PUT, "/api/v1/admin/inventory/items/*")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.INVENTORY_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/admin/users/**", "/api/v1/admin/roles",
								"/api/v1/admin/permissions")
						.hasAnyAuthority(ADMIN_AUTHORITY)
						.requestMatchers(HttpMethod.POST, "/api/v1/admin/users")
						.hasAnyAuthority(ADMIN_AUTHORITY)
						.requestMatchers(HttpMethod.PUT, "/api/v1/admin/users/*")
						.hasAnyAuthority(ADMIN_AUTHORITY)
						.requestMatchers(HttpMethod.POST, "/api/v1/admin/roles")
						.hasAnyAuthority(ADMIN_AUTHORITY)
						.requestMatchers(HttpMethod.PUT, "/api/v1/admin/roles/**")
						.hasAnyAuthority(ADMIN_AUTHORITY)
						.requestMatchers("/api/v1/admin/promotions/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.RATES_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/admin/reports/**", "/api/v1/admin/audit-logs")
						.hasAnyAuthority(ADMIN_AUTHORITY)
						.requestMatchers(HttpMethod.GET, "/api/v1/admin/bookings/*/receipt")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.FOLIOS_READ)
						.requestMatchers(HttpMethod.GET, "/api/v1/rooms/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOMS_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/rooms")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOMS_WRITE)
						.requestMatchers(HttpMethod.PUT, "/api/v1/rooms/*")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOMS_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/room-types/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOM_TYPES_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/room-types")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOM_TYPES_WRITE)
						.requestMatchers(HttpMethod.PUT, "/api/v1/room-types/*")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOM_TYPES_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/room-features")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOM_FEATURES_READ)
						.requestMatchers(HttpMethod.GET, "/api/v1/rates")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.RATES_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/rates")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.RATES_WRITE)
						.requestMatchers(HttpMethod.PUT, "/api/v1/rates/*")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.RATES_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/guests/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.GUESTS_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/guests")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.GUESTS_WRITE)
						.requestMatchers(HttpMethod.PUT, "/api/v1/guests/*")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.GUESTS_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/bookings/*/companions")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.BOOKING_COMPANIONS_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/companions")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.BOOKING_COMPANIONS_WRITE)
						.requestMatchers(HttpMethod.PUT, "/api/v1/bookings/*/companions/*")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.BOOKING_COMPANIONS_WRITE)
						.requestMatchers(HttpMethod.DELETE, "/api/v1/bookings/*/companions/*")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.BOOKING_COMPANIONS_WRITE)
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/check-in")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.BOOKINGS_CHECK_IN)
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/check-out")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.BOOKINGS_CHECK_OUT)
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/confirm")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.BOOKINGS_WRITE)
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/cancel")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.BOOKINGS_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/guest-accounts")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.FOLIOS_READ)
						.requestMatchers(HttpMethod.GET, "/api/v1/charges")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.CHARGES_READ)
						.requestMatchers(HttpMethod.GET, "/api/v1/payments")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.PAYMENTS_READ)
						.requestMatchers(HttpMethod.GET, "/api/v1/deposits")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.DEPOSITS_READ)
						.requestMatchers(HttpMethod.GET, "/api/v1/bookings/*/payments")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.PAYMENTS_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/payments")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.PAYMENTS_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/bookings/*/deposits")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.DEPOSITS_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/deposits")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.DEPOSITS_WRITE)
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/deposits/*/refund")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.DEPOSITS_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/bookings/*/folio")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.FOLIOS_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/folio/open")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.FOLIOS_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/bookings/*/charges")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.CHARGES_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/charges")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.CHARGES_WRITE)
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings/*/charges/*/void")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.CHARGES_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/bookings/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.BOOKINGS_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/bookings")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.BOOKINGS_WRITE)
						.requestMatchers(HttpMethod.PUT, "/api/v1/bookings/*")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.BOOKINGS_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/housekeeping/rooms/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.HOUSEKEEPING_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/housekeeping/rooms/*/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.HOUSEKEEPING_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/room-service/products")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOM_SERVICE_READ)
						.requestMatchers(HttpMethod.GET, "/api/v1/room-service/orders/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOM_SERVICE_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/room-service/orders/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOM_SERVICE_WRITE)
						.requestMatchers(HttpMethod.PATCH, "/api/v1/room-service/orders/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.ROOM_SERVICE_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/inventory/items/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.INVENTORY_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/inventory/items/*/movements")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.INVENTORY_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/cash-sessions/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.CASH_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/cash-sessions/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.CASH_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/concierge/requests/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.CONCIERGE_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/concierge/requests/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.CONCIERGE_WRITE)
						.requestMatchers(HttpMethod.PUT, "/api/v1/concierge/requests/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.CONCIERGE_WRITE)
						.requestMatchers(HttpMethod.GET, "/api/v1/service-requests/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.SERVICE_REQUESTS_READ)
						.requestMatchers(HttpMethod.POST, "/api/v1/service-requests/**")
						.hasAnyAuthority(ADMIN_AUTHORITY, SecurityPermissions.SERVICE_REQUESTS_WRITE)
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	private static void writeErrorResponse(
			HttpServletRequest request,
			HttpServletResponse response,
			ObjectMapper objectMapper,
			HttpStatus status
	) throws java.io.IOException {
		ApiErrorResponse errorResponse = ApiErrorResponse.of(
				status.value(),
				status.getReasonPhrase(),
				status.getReasonPhrase(),
				request.getRequestURI()
		);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), errorResponse);
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
