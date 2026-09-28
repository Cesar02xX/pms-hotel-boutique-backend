package com.aurora.pms.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

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
				Arguments.of(HttpMethod.DELETE, "/api/v1/bookings/" + id + "/companions/" + id)
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
}
