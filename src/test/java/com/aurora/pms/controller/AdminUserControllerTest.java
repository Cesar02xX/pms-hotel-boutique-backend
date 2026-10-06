package com.aurora.pms.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.model.Role;
import com.aurora.pms.repository.RolePermissionRepository;
import com.aurora.pms.repository.RoleRepository;
import com.aurora.pms.security.SecurityPermissions;
import com.jayway.jsonpath.JsonPath;

class AdminUserControllerTest extends AbstractCatalogApiTest {

	private static final String ADMIN_ROOT = "/api/v1/admin";

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private RolePermissionRepository rolePermissionRepository;

	private final List<UUID> roleIds = new ArrayList<>();

	@AfterEach
	@Transactional
	void cleanUpRoles() {
		roleIds.forEach(rolePermissionRepository::deleteByIdRoleId);
		roleRepository.deleteAllById(roleIds);
		roleIds.clear();
	}

	@Test
	void listPermissionsReturnsCatalogKeys() throws Exception {
		mockMvc.perform(get(ADMIN_ROOT + "/permissions").with(adminUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].key", hasItem(SecurityPermissions.ROOMS_READ)))
				.andExpect(jsonPath("$[*].name").isNotEmpty());
	}

	@Test
	void createRolePersistsPermissions() throws Exception {
		String suffix = uniqueSuffix();

		MvcResult result = mockMvc.perform(post(ADMIN_ROOT + "/roles")
						.with(adminUser())
						.contentType("application/json")
						.content("""
								{
								  "code": "front desk lead %s",
								  "name": "Front Desk Lead %s",
								  "active": true,
								  "permissions": ["rooms.read", "bookings.read"]
								}
								""".formatted(suffix, suffix)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("FRONT_DESK_LEAD_" + suffix.toUpperCase()))
				.andExpect(jsonPath("$.name").value("Front Desk Lead " + suffix))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.permissions", hasItem(SecurityPermissions.ROOMS_READ)))
				.andExpect(jsonPath("$.permissions", hasItem(SecurityPermissions.BOOKINGS_READ)))
				.andReturn();

		roleIds.add(UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id")));
	}

	@Test
	void updateRolePermissionsReplacesExistingSet() throws Exception {
		Role role = createRole("OPS_TEST_" + uniqueSuffix(), "Operations Test");

		mockMvc.perform(put(ADMIN_ROOT + "/roles/{id}/permissions", role.getId())
						.with(adminUser())
						.contentType("application/json")
						.content("""
								{
								  "permissions": ["rooms.read", "bookings.read"]
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.permissions", hasItem(SecurityPermissions.ROOMS_READ)))
				.andExpect(jsonPath("$.permissions", hasItem(SecurityPermissions.BOOKINGS_READ)));

		mockMvc.perform(put(ADMIN_ROOT + "/roles/{id}/permissions", role.getId())
						.with(adminUser())
						.contentType("application/json")
						.content("""
								{
								  "permissions": ["housekeeping.read"]
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.permissions", hasItem(SecurityPermissions.HOUSEKEEPING_READ)))
				.andExpect(jsonPath("$.permissions", not(hasItem(SecurityPermissions.ROOMS_READ))));
	}

	@Test
	void updateRoleRejectsUnknownPermission() throws Exception {
		Role role = createRole("UNKNOWN_PERM_TEST_" + uniqueSuffix(), "Unknown Permission Test");

		mockMvc.perform(put(ADMIN_ROOT + "/roles/{id}/permissions", role.getId())
						.with(adminUser())
						.contentType("application/json")
						.content("""
								{
								  "permissions": ["rooms.read", "does.not.exist"]
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Unknown permissions: does.not.exist"));
	}

	@Test
	void adminRoleCannotBeDeactivatedAndKeepsRequiredPanelPermissions() throws Exception {
		Role admin = roleRepository.findByCode("admin")
				.orElseGet(() -> createRole("admin", "Admin"));

		mockMvc.perform(put(ADMIN_ROOT + "/roles/{id}", admin.getId())
						.with(adminUser())
						.contentType("application/json")
						.content("""
								{
								  "active": false
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("ADMIN role cannot be deactivated"));

		mockMvc.perform(put(ADMIN_ROOT + "/roles/{id}/permissions", admin.getId())
						.with(adminUser())
						.contentType("application/json")
						.content("""
								{
								  "permissions": ["guest-portal.home"]
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.permissions", hasItem(SecurityPermissions.GUEST_PORTAL_HOME)))
				.andExpect(jsonPath("$.permissions", hasItem(SecurityPermissions.ROOMS_READ)))
				.andExpect(jsonPath("$.permissions", hasItem(SecurityPermissions.ROOMS_WRITE)))
				.andExpect(jsonPath("$.permissions", hasItem(SecurityPermissions.RATES_WRITE)))
				.andExpect(jsonPath("$.permissions", hasItem(SecurityPermissions.CASH_WRITE)))
				.andExpect(jsonPath("$.permissions", not(hasItem(SecurityPermissions.GUEST_PORTAL_STAY))));
	}

	private Role createRole(String code, String name) {
		Role role = new Role();
		role.setCode(code);
		role.setName(name);
		role.setActive(true);
		role.setCreatedAt(now());
		role.setUpdatedAt(now());
		role = roleRepository.save(role);
		roleIds.add(role.getId());
		return role;
	}

	private RequestPostProcessor adminUser() {
		return user("admin@aurora.test").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}
}
