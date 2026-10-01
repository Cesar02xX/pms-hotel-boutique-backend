package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateUserRequest;
import com.aurora.pms.dto.request.UpdateUserRequest;
import com.aurora.pms.dto.response.RoleResponse;
import com.aurora.pms.dto.response.UserAdminResponse;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.model.Role;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.UserStatus;
import com.aurora.pms.repository.RolePermissionRepository;
import com.aurora.pms.repository.RoleRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.service.AdminUserService;

@Service
public class AdminUserServiceImpl implements AdminUserService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final RolePermissionRepository rolePermissionRepository;
	private final PasswordEncoder passwordEncoder;

	public AdminUserServiceImpl(
			UserRepository userRepository,
			RoleRepository roleRepository,
			RolePermissionRepository rolePermissionRepository,
			PasswordEncoder passwordEncoder
	) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
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
				.map(role -> new RoleResponse(
						role.getId(),
						role.getCode(),
						role.getName(),
						role.getActive(),
						rolePermissionRepository.findByRoleIdWithPermission(role.getId()).stream()
								.map(rolePermission -> rolePermission.getPermission().getKey())
								.sorted()
								.toList()
				))
				.toList();
	}

	private User getUser(UUID id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
	}

	private Role getRole(UUID id) {
		return roleRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
	}

	private UserAdminResponse toUserResponse(User user) {
		return new UserAdminResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
				user.getRole().getId(), user.getRole().getCode(), user.getStatus(), user.getCreatedAt(),
				user.getUpdatedAt());
	}
}
