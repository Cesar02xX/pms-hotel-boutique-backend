package com.aurora.pms.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.CreateUserRequest;
import com.aurora.pms.dto.request.UpdateUserRequest;
import com.aurora.pms.dto.response.RoleResponse;
import com.aurora.pms.dto.response.UserAdminResponse;
import com.aurora.pms.service.AdminUserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminUserController {

	private final AdminUserService adminUserService;

	public AdminUserController(AdminUserService adminUserService) {
		this.adminUserService = adminUserService;
	}

	@GetMapping("/users")
	public ResponseEntity<List<UserAdminResponse>> findUsers() {
		return ResponseEntity.ok(adminUserService.findUsers());
	}

	@GetMapping("/users/{id}")
	public ResponseEntity<UserAdminResponse> findUser(@PathVariable UUID id) {
		return ResponseEntity.ok(adminUserService.findUser(id));
	}

	@PostMapping("/users")
	public ResponseEntity<UserAdminResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(adminUserService.createUser(request));
	}

	@PutMapping("/users/{id}")
	public ResponseEntity<UserAdminResponse> updateUser(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateUserRequest request
	) {
		return ResponseEntity.ok(adminUserService.updateUser(id, request));
	}

	@GetMapping("/roles")
	public ResponseEntity<List<RoleResponse>> findRoles() {
		return ResponseEntity.ok(adminUserService.findRoles());
	}
}
