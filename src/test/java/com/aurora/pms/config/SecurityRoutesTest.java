package com.aurora.pms.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.model.Permission;
import com.aurora.pms.model.Role;
import com.aurora.pms.model.RolePermission;
import com.aurora.pms.model.RolePermissionId;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.UserStatus;
import com.aurora.pms.repository.PermissionRepository;
import com.aurora.pms.repository.RolePermissionRepository;
import com.aurora.pms.repository.RoleRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.security.JwtService;

@SpringBootTest(properties = "security.jwt.secret=01234567890123456789012345678901")
class SecurityRoutesTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PermissionRepository permissionRepository;

	@Autowired
	private RolePermissionRepository rolePermissionRepository;

	private MockMvc mockMvc;
	private User testUser;
	private Role testRole;
	private Permission testPermission;
	private RolePermissionId testRolePermissionId;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	@AfterEach
	void tearDown() {
		if (testRolePermissionId != null) {
			rolePermissionRepository.deleteById(testRolePermissionId);
			testRolePermissionId = null;
		}
		if (testUser != null && testUser.getId() != null) {
			userRepository.deleteById(testUser.getId());
			testUser = null;
		}
		if (testRole != null && testRole.getId() != null) {
			roleRepository.deleteById(testRole.getId());
			testRole = null;
		}
		if (testPermission != null && testPermission.getId() != null) {
			permissionRepository.deleteById(testPermission.getId());
			testPermission = null;
		}
	}

	@Test
	void privateEndpointWithoutJwtReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/private-check"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.message").value("Unauthorized"));
	}

	@Test
	void protectedEndpointWithValidJwtReturnsOk() throws Exception {
		createProtectedRouteUser();
		UserDetails userDetails = org.springframework.security.core.userdetails.User
				.withUsername(testUser.getEmail())
				.password("password")
				.authorities("ROLE_AUTH_TEST", "auth.integration.read")
				.build();
		String accessToken = jwtService.generateAccessToken(userDetails);

		mockMvc.perform(get("/api/v1/test/protected")
						.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("protected"));
	}

	@Test
	void authenticatedUserCanReadOnlyTheirEffectivePermissionKeys() throws Exception {
		createProtectedRouteUser();
		UserDetails userDetails = org.springframework.security.core.userdetails.User
				.withUsername(testUser.getEmail())
				.password("password")
				.authorities("ROLE_AUTH_TEST", testPermission.getKey())
				.build();
		String accessToken = jwtService.generateAccessToken(userDetails);

		mockMvc.perform(get("/api/v1/auth/permissions")
					.header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.permissions.length()").value(1))
				.andExpect(jsonPath("$.permissions[0]").value(testPermission.getKey()));
	}

	@Test
	void currentPermissionKeysRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/auth/permissions"))
				.andExpect(status().isUnauthorized());
	}

	private void createProtectedRouteUser() {
		String suffix = UUID.randomUUID().toString();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		testRole = new Role();
		testRole.setCode("auth_test_" + suffix);
		testRole.setName("Auth Test " + suffix);
		testRole.setActive(true);
		testRole.setCreatedAt(now);
		testRole.setUpdatedAt(now);
		testRole = roleRepository.save(testRole);

		testPermission = new Permission();
		testPermission.setKey("auth.integration.read." + suffix);
		testPermission.setName("Auth Integration Read " + suffix);
		testPermission.setDescription("Temporary permission for auth integration tests");
		testPermission.setCreatedAt(now);
		testPermission.setUpdatedAt(now);
		testPermission = permissionRepository.save(testPermission);

		testRolePermissionId = new RolePermissionId();
		testRolePermissionId.setRoleId(testRole.getId());
		testRolePermissionId.setPermissionId(testPermission.getId());

		RolePermission rolePermission = new RolePermission();
		rolePermission.setId(testRolePermissionId);
		rolePermission.setRole(testRole);
		rolePermission.setPermission(testPermission);
		rolePermissionRepository.save(rolePermission);

		testUser = new User();
		testUser.setFirstName("Route");
		testUser.setLastName("User");
		testUser.setEmail("route.user." + suffix + "@aurora.test");
		testUser.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcd");
		testUser.setRole(testRole);
		testUser.setStatus(UserStatus.active);
		testUser.setCreatedAt(now);
		testUser.setUpdatedAt(now);
		testUser = userRepository.save(testUser);
	}

	@Test
	void healthEndpointIsPublic() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
				.andExpect(status().isOk());
	}

	@Test
	void loginEndpointIsPublicAndValidatedByController() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void guestLoginEndpointIsPublicAndValidatedByController() throws Exception {
		mockMvc.perform(post("/api/v1/guest/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void refreshEndpointIsPublicAndValidatedByController() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void refreshEndpointRejectsUnknownRefreshTokenWithUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\":\"unknown-refresh-token\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutEndpointIsPublicAndValidatedByController() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void logoutEndpointRejectsUnknownRefreshTokenWithUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\":\"unknown-refresh-token\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void publicBookingEndpointsDoNotRequireJwt() throws Exception {
		mockMvc.perform(get("/api/v1/public/room-types"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/public/rates"))
				.andExpect(status().isOk());
		// Llegan al controller: el 400 viene de la validación, no de la seguridad.
		mockMvc.perform(get("/api/v1/public/availability"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Check-in date is required"));
		mockMvc.perform(post("/api/v1/public/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"));
	}

	@Test
	void publicBookingEndpointsIgnoreAnInvalidJwt() throws Exception {
		mockMvc.perform(get("/api/v1/public/room-types")
						.header("Authorization", "Bearer invalid-token"))
				.andExpect(status().isOk());
	}

	@Test
	void onlyTheExactPublicRoutesAndMethodsAreOpen() throws Exception {
		mockMvc.perform(get("/api/v1/public/bookings"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/v1/public/bookings"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/public/room-types"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/v1/public/rates"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/public/anything-else"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void administrativeBookingCatalogEndpointsStillRequireJwt() throws Exception {
		mockMvc.perform(get("/api/v1/room-types"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/rates"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/rooms"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/bookings"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/guests"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/guests")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@TestConfiguration
	static class ProtectedTestControllerConfiguration {

		@Bean
		ProtectedTestController protectedTestController() {
			return new ProtectedTestController();
		}
	}

	@RestController
	static class ProtectedTestController {

		@GetMapping("/api/v1/test/protected")
		java.util.Map<String, String> protectedEndpoint() {
			return java.util.Map.of("status", "protected");
		}
	}
}
