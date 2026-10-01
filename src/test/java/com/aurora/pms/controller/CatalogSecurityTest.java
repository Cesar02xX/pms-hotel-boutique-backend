package com.aurora.pms.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import com.aurora.pms.security.SecurityPermissions;

/**
 * Verifica que ninguna ruta del catálogo quedó pública: sin token o con un
 * token inválido, todas responden 401 con el formato de error existente.
 */
class CatalogSecurityTest extends AbstractCatalogApiTest {

	static Stream<Arguments> catalogEndpoints() {
		String id = UUID.randomUUID().toString();
		return Stream.of(
				Arguments.of(HttpMethod.GET, "/api/v1/rooms"),
				Arguments.of(HttpMethod.GET, "/api/v1/rooms/" + id),
				Arguments.of(HttpMethod.POST, "/api/v1/rooms"),
				Arguments.of(HttpMethod.PUT, "/api/v1/rooms/" + id),
				Arguments.of(HttpMethod.GET, "/api/v1/room-types"),
				Arguments.of(HttpMethod.GET, "/api/v1/room-types/" + id),
				Arguments.of(HttpMethod.POST, "/api/v1/room-types"),
				Arguments.of(HttpMethod.PUT, "/api/v1/room-types/" + id),
				Arguments.of(HttpMethod.GET, "/api/v1/room-features"),
				Arguments.of(HttpMethod.GET, "/api/v1/rates"),
				Arguments.of(HttpMethod.POST, "/api/v1/rates"),
				Arguments.of(HttpMethod.PUT, "/api/v1/rates/" + id),
				Arguments.of(HttpMethod.GET, "/api/v1/guests"),
				Arguments.of(HttpMethod.GET, "/api/v1/guests/" + id),
				Arguments.of(HttpMethod.POST, "/api/v1/guests"),
				Arguments.of(HttpMethod.PUT, "/api/v1/guests/" + id),
				Arguments.of(HttpMethod.GET, "/api/v1/bookings"),
				Arguments.of(HttpMethod.GET, "/api/v1/bookings/" + id),
				Arguments.of(HttpMethod.POST, "/api/v1/bookings"),
				Arguments.of(HttpMethod.PUT, "/api/v1/bookings/" + id),
				Arguments.of(HttpMethod.POST, "/api/v1/bookings/" + id + "/check-in"),
				Arguments.of(HttpMethod.GET, "/api/v1/bookings/" + id + "/companions"),
				Arguments.of(HttpMethod.POST, "/api/v1/bookings/" + id + "/companions"),
				Arguments.of(HttpMethod.PUT, "/api/v1/bookings/" + id + "/companions/" + id),
				Arguments.of(HttpMethod.DELETE, "/api/v1/bookings/" + id + "/companions/" + id),
				Arguments.of(HttpMethod.GET, "/api/v1/housekeeping/rooms"),
				Arguments.of(HttpMethod.GET, "/api/v1/housekeeping/rooms/" + id),
				Arguments.of(HttpMethod.POST, "/api/v1/housekeeping/rooms/" + id + "/start"),
				Arguments.of(HttpMethod.POST, "/api/v1/housekeeping/rooms/" + id + "/complete"),
				Arguments.of(HttpMethod.POST, "/api/v1/housekeeping/rooms/" + id + "/inspect"),
				Arguments.of(HttpMethod.GET, "/api/v1/room-service/products"),
				Arguments.of(HttpMethod.GET, "/api/v1/room-service/orders"),
				Arguments.of(HttpMethod.GET, "/api/v1/room-service/orders/" + id),
				Arguments.of(HttpMethod.POST, "/api/v1/room-service/orders"),
				Arguments.of(HttpMethod.POST, "/api/v1/room-service/orders/" + id + "/status")
		);
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("catalogEndpoints")
	void endpointWithoutTokenReturnsUnauthorized(HttpMethod method, String path) throws Exception {
		mockMvc.perform(request(method, path)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("catalogEndpoints")
	void endpointWithInvalidTokenReturnsUnauthorized(HttpMethod method, String path) throws Exception {
		mockMvc.perform(request(method, path)
						.header("Authorization", "Bearer invalid.jwt.token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("catalogEndpoints")
	void endpointWithAuthenticatedUserWithoutPermissionReturnsForbidden(HttpMethod method, String path) throws Exception {
		mockMvc.perform(request(method, path)
						.with(user("limited@aurora.test").authorities(() -> "unrelated.permission"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.error").value("Forbidden"))
				.andExpect(jsonPath("$.message").value("Forbidden"));
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("readableCatalogEndpoints")
	void endpointWithMatchingReadPermissionIsAuthorized(HttpMethod method, String path, String permission) throws Exception {
		mockMvc.perform(request(method, path)
						.with(user("reader@aurora.test").authorities(() -> permission)))
				.andExpect(status().isOk());
	}

	static Stream<Arguments> readableCatalogEndpoints() {
		String id = UUID.randomUUID().toString();
		return Stream.of(
				Arguments.of(HttpMethod.GET, "/api/v1/rooms", SecurityPermissions.ROOMS_READ),
				Arguments.of(HttpMethod.GET, "/api/v1/room-types", SecurityPermissions.ROOM_TYPES_READ),
				Arguments.of(HttpMethod.GET, "/api/v1/room-features", SecurityPermissions.ROOM_FEATURES_READ),
				Arguments.of(HttpMethod.GET, "/api/v1/rates", SecurityPermissions.RATES_READ),
				Arguments.of(HttpMethod.GET, "/api/v1/guests", SecurityPermissions.GUESTS_READ),
				Arguments.of(HttpMethod.GET, "/api/v1/bookings", SecurityPermissions.BOOKINGS_READ),
				Arguments.of(HttpMethod.GET, "/api/v1/housekeeping/rooms", SecurityPermissions.HOUSEKEEPING_READ),
				Arguments.of(HttpMethod.GET, "/api/v1/room-service/products", SecurityPermissions.ROOM_SERVICE_READ),
				Arguments.of(HttpMethod.GET, "/api/v1/room-service/orders", SecurityPermissions.ROOM_SERVICE_READ)
		);
	}
}
