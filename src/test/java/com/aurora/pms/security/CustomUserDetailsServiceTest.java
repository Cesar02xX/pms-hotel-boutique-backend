package com.aurora.pms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.aurora.pms.model.Permission;
import com.aurora.pms.model.Role;
import com.aurora.pms.model.RolePermission;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.UserStatus;
import com.aurora.pms.repository.RolePermissionRepository;
import com.aurora.pms.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

	private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuvabcd";

	@Mock
	private UserRepository userRepository;

	@Mock
	private RolePermissionRepository rolePermissionRepository;

	@InjectMocks
	private CustomUserDetailsService customUserDetailsService;

	@Test
	void loadUserByUsernameBuildsUserDetailsFromDatabaseUser() {
		Role role = role("room_service");
		User user = user("room.service@aurora.test", UserStatus.active, role);
		RolePermission readOrders = rolePermission("orders:read");
		RolePermission duplicateReadOrders = rolePermission("orders:read");
		RolePermission updateOrders = rolePermission("orders:update");

		when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
		when(rolePermissionRepository.findByRoleIdWithPermission(role.getId()))
				.thenReturn(List.of(readOrders, duplicateReadOrders, updateOrders));

		UserDetails userDetails = customUserDetailsService.loadUserByUsername(user.getEmail());

		assertThat(userDetails.getUsername()).isEqualTo(user.getEmail());
		assertThat(userDetails.getPassword()).isEqualTo(PASSWORD_HASH);
		assertThat(userDetails.isEnabled()).isTrue();
		assertThat(authorityNames(userDetails))
				.containsExactlyInAnyOrder("ROLE_ROOM_SERVICE", "orders:read", "orders:update");
	}

	@Test
	void loadUserByUsernameThrowsWhenUserDoesNotExist() {
		when(userRepository.findByEmail("missing@aurora.test")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("missing@aurora.test"))
				.isInstanceOf(UsernameNotFoundException.class);
	}

	@Test
	void inactiveUserIsDisabled() {
		Role role = role("reception");
		User user = user("inactive@aurora.test", UserStatus.inactive, role);

		when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
		when(rolePermissionRepository.findByRoleIdWithPermission(role.getId())).thenReturn(List.of());

		UserDetails userDetails = customUserDetailsService.loadUserByUsername(user.getEmail());

		assertThat(userDetails.isEnabled()).isFalse();
		assertThat(authorityNames(userDetails)).containsExactly("ROLE_RECEPTION");
	}

	private Set<String> authorityNames(UserDetails userDetails) {
		return userDetails.getAuthorities().stream()
				.map(authority -> authority.getAuthority())
				.collect(Collectors.toSet());
	}

	private User user(String email, UserStatus status, Role role) {
		User user = new User();
		user.setId(UUID.randomUUID());
		user.setFirstName("Test");
		user.setLastName("User");
		user.setEmail(email);
		user.setPasswordHash(PASSWORD_HASH);
		user.setRole(role);
		user.setStatus(status);
		user.setCreatedAt(OffsetDateTime.now());
		user.setUpdatedAt(OffsetDateTime.now());
		return user;
	}

	private Role role(String code) {
		Role role = new Role();
		role.setId(UUID.randomUUID());
		role.setCode(code);
		role.setName(code);
		role.setActive(true);
		role.setCreatedAt(OffsetDateTime.now());
		role.setUpdatedAt(OffsetDateTime.now());
		return role;
	}

	private RolePermission rolePermission(String key) {
		Permission permission = new Permission();
		permission.setId(UUID.randomUUID());
		permission.setKey(key);
		permission.setName(key);
		permission.setCreatedAt(OffsetDateTime.now());
		permission.setUpdatedAt(OffsetDateTime.now());

		RolePermission rolePermission = new RolePermission();
		rolePermission.setPermission(permission);
		return rolePermission;
	}
}
