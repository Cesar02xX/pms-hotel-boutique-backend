package com.aurora.pms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.LoginRequest;
import com.aurora.pms.dto.request.RefreshTokenRequest;
import com.aurora.pms.dto.response.AuthResponse;
import com.aurora.pms.dto.response.CurrentPermissionsResponse;
import com.aurora.pms.service.AuthService;
import com.aurora.pms.service.CurrentPermissionsService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final CurrentPermissionsService currentPermissionsService;

	public AuthController(
			AuthService authService,
			CurrentPermissionsService currentPermissionsService
	) {
		this.authService = authService;
		this.currentPermissionsService = currentPermissionsService;
	}

	@GetMapping("/permissions")
	@Operation(summary = "Get effective permissions for the authenticated user")
	public ResponseEntity<CurrentPermissionsResponse> permissions(Authentication authentication) {
		return ResponseEntity.ok(new CurrentPermissionsResponse(
				currentPermissionsService.findFor(authentication.getPrincipal())));
	}

	@PostMapping("/login")
	@Operation(summary = "Authenticate a user and issue access and refresh tokens")
	@SecurityRequirements
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(authService.login(request));
	}

	@PostMapping("/refresh")
	@Operation(summary = "Rotate a refresh token and issue a new access token")
	@SecurityRequirements
	public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return ResponseEntity.ok(authService.refresh(request));
	}

	@PostMapping("/logout")
	@Operation(summary = "Revoke a refresh token")
	@SecurityRequirements
	public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(request);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
