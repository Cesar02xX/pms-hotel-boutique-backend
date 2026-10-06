package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateRoleRequest;
import com.aurora.pms.dto.request.CreateUserRequest;
import com.aurora.pms.dto.request.UpdateRolePermissionsRequest;
import com.aurora.pms.dto.request.UpdateRoleRequest;
import com.aurora.pms.dto.request.UpdateUserRequest;
import com.aurora.pms.dto.response.PermissionResponse;
import com.aurora.pms.dto.response.RoleResponse;
import com.aurora.pms.dto.response.UserAdminResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.exception.ResourceNotFoundException;
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
import com.aurora.pms.security.SecurityPermissions;
import com.aurora.pms.service.AdminUserService;

@Service
public class AdminUserServiceImpl implements AdminUserService {

	private static final String ADMIN_ROLE_CODE = "admin";
	private static final List<String> REQUIRED_ADMIN_PERMISSION_KEYS = List.of(
			SecurityPermissions.BOOKINGS_READ,
			SecurityPermissions.ROOMS_READ,
			SecurityPermissions.ROOMS_WRITE,
			SecurityPermissions.ROOM_TYPES_READ,
			SecurityPermissions.ROOM_TYPES_WRITE,
			SecurityPermissions.ROOM_FEATURES_READ,
			SecurityPermissions.RATES_READ,
			SecurityPermissions.RATES_WRITE,
			SecurityPermissions.ROOM_SERVICE_READ,
			SecurityPermissions.ROOM_SERVICE_WRITE,
			SecurityPermissions.CONCIERGE_READ,
			SecurityPermissions.CONCIERGE_WRITE,
			SecurityPermissions.HOUSEKEEPING_READ,
			SecurityPermissions.PAYMENTS_READ,
			SecurityPermissions.DEPOSITS_READ,
			SecurityPermissions.CHARGES_READ,
			SecurityPermissions.INVENTORY_READ,
			SecurityPermissions.INVENTORY_WRITE,
			SecurityPermissions.CASH_READ,
			SecurityPermissions.CASH_WRITE
	);

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PermissionRepository permissionRepository;
	private final RolePermissionRepository rolePermissionRepository;
	private final PasswordEncoder passwordEncoder;

	public AdminUserServiceImpl(
			UserRepository userRepository,
			RoleRepository roleRepository,
			PermissionRepository permissionRepository,
			RolePermissionRepository rolePermissionRepository,
			PasswordEncoder passwordEncoder
	) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.permissionRepository = permissionRepository;
		this.rolePermissionRepository = rolePermissionRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional(readOnly = true)
	public List<UserAdminResponse> findUsers() {
		return userRepository.findAll().stream()
				.sorted(Comparator.comparing(User::getEmail))
				.map(this::toUserResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public UserAdminResponse findUser(UUID id) {
		return toUserResponse(getUser(id));
	}

	@Override
	@Transactional
	public UserAdminResponse createUser(CreateUserRequest request) {
		if (userRepository.existsByEmailIgnoreCase(request.email())) {
			throw new ConflictException("User email already exists: " + request.email());
		}
		OffsetDateTime now = OffsetDateTime.now();
		User user = new User();
		user.setFirstName(request.firstName().trim());
		user.setLastName(request.lastName().trim());
		user.setEmail(request.email().trim().toLowerCase());
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setRole(getRole(request.roleId()));
		user.setStatus(UserStatus.active);
		user.setCreatedAt(now);
		user.setUpdatedAt(now);
		return toUserResponse(userRepository.save(user));
	}

	@Override
	@Transactional
	public UserAdminResponse updateUser(UUID id, UpdateUserRequest request) {
		User user = getUser(id);
		if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())
				&& userRepository.existsByEmailIgnoreCase(request.email())) {
			throw new ConflictException("User email already exists: " + request.email());
		}
		if (request.firstName() != null) {
			user.setFirstName(request.firstName().trim());
		}
		if (request.lastName() != null) {
			user.setLastName(request.lastName().trim());
		}
		if (request.email() != null) {
			user.setEmail(request.email().trim().toLowerCase());
		}
		if (request.roleId() != null) {
			user.setRole(getRole(request.roleId()));
		}
		if (request.status() != null) {
			user.setStatus(request.status());
		}
		user.setUpdatedAt(OffsetDateTime.now());
		return toUserResponse(userRepository.save(user));
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoleResponse> findRoles() {
		return roleRepository.findAll().stream()
				.sorted(Comparator.comparing(Role::getCode))
				.map(this::toRoleResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<PermissionResponse> findPermissions() {
		return permissionRepository.findAllByOrderByKey().stream()
				.map(permission -> new PermissionResponse(
						permission.getId(),
						permission.getKey(),
						permission.getName(),
						permission.getDescription()
				))
				.toList();
	}

	@Override
	@Transactional
	public RoleResponse createRole(CreateRoleRequest request) {
		String code = normalizeCode(request.code());
		if (roleRepository.existsByCodeIgnoreCase(code)) {
			throw new ConflictException("Role code already exists: " + code);
		}
		OffsetDateTime now = OffsetDateTime.now();
		Role role = new Role();
		role.setCode(code);
		role.setName(request.name().trim());
		role.setActive(request.active() == null || request.active());
		role.setCreatedAt(now);
		role.setUpdatedAt(now);
		role = roleRepository.save(role);
		replacePermissions(role, request.permissions());
		return toRoleResponse(role);
	}

	@Override
	@Transactional
	public RoleResponse updateRole(UUID id, UpdateRoleRequest request) {
		Role role = getRole(id);
		if (request.name() != null && !request.name().trim().isEmpty()) {
			role.setName(request.name().trim());
		}
		if (request.active() != null) {
			ensureCanChangeActive(role, request.active());
			role.setActive(request.active());
		}
		role.setUpdatedAt(OffsetDateTime.now());
		return toRoleResponse(roleRepository.save(role));
	}

	@Override
	@Transactional
	public RoleResponse updateRolePermissions(UUID id, UpdateRolePermissionsRequest request) {
		Role role = getRole(id);
		replacePermissions(role, withRequiredAdminPermissions(role, request.permissions()));
		role.setUpdatedAt(OffsetDateTime.now());
		return toRoleResponse(roleRepository.save(role));
	}

	private User getUser(UUID id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
	}

	private Role getRole(UUID id) {
		return roleRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
	}

	private RoleResponse toRoleResponse(Role role) {
		return new RoleResponse(
				role.getId(),
				role.getCode(),
				role.getName(),
				role.getActive(),
				rolePermissionRepository.findByRoleIdWithPermission(role.getId()).stream()
						.map(rolePermission -> rolePermission.getPermission().getKey())
						.sorted()
						.toList()
		);
	}

	private void replacePermissions(Role role, List<String> permissionKeys) {
		List<String> normalizedKeys = normalizePermissionKeys(permissionKeys);
		Map<String, Permission> permissionsByKey = permissionRepository.findByKeyIn(normalizedKeys).stream()
				.collect(Collectors.toMap(Permission::getKey, Function.identity()));
		List<String> missing = normalizedKeys.stream()
				.filter(key -> !permissionsByKey.containsKey(key))
				.toList();
		if (!missing.isEmpty()) {
			throw new BadRequestException("Unknown permissions: " + String.join(", ", missing));
		}
		rolePermissionRepository.deleteByIdRoleId(role.getId());
		List<RolePermission> rolePermissions = normalizedKeys.stream()
				.map(key -> toRolePermission(role, permissionsByKey.get(key)))
				.toList();
		rolePermissionRepository.saveAll(rolePermissions);
	}

	private static RolePermission toRolePermission(Role role, Permission permission) {
		RolePermissionId id = new RolePermissionId();
		id.setRoleId(role.getId());
		id.setPermissionId(permission.getId());
		RolePermission rolePermission = new RolePermission();
		rolePermission.setId(id);
		rolePermission.setRole(role);
		rolePermission.setPermission(permission);
		return rolePermission;
	}

	private static List<String> normalizePermissionKeys(List<String> permissionKeys) {
		Set<String> seen = new HashSet<>();
		return permissionKeys.stream()
				.map(String::trim)
				.filter(key -> !key.isEmpty())
				.filter(seen::add)
				.sorted()
				.toList();
	}

	private static String normalizeCode(String code) {
		String normalized = code.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
		if (!normalized.matches("[A-Z0-9_]+")) {
			throw new BadRequestException("Role code can only contain letters, numbers, spaces, hyphens or underscores");
		}
		return normalized;
	}

	private static void ensureCanChangeActive(Role role, boolean nextActive) {
		if (isAdminRole(role) && !nextActive) {
			throw new BadRequestException("ADMIN role cannot be deactivated");
		}
	}

	private static List<String> withRequiredAdminPermissions(Role role, List<String> permissionKeys) {
		if (!isAdminRole(role)) {
			return permissionKeys;
		}
		Set<String> merged = new HashSet<>(permissionKeys);
		merged.addAll(REQUIRED_ADMIN_PERMISSION_KEYS);
		return merged.stream().sorted().toList();
	}

	private static boolean isAdminRole(Role role) {
		return ADMIN_ROLE_CODE.equalsIgnoreCase(role.getCode());
	}

	private UserAdminResponse toUserResponse(User user) {
		return new UserAdminResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
				user.getRole().getId(), user.getRole().getCode(), user.getStatus(), user.getCreatedAt(),
				user.getUpdatedAt());
	}
}
