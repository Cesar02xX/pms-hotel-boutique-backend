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

import com.aurora.pms.dto.request.CreateGuestRequest;
import com.aurora.pms.dto.request.UpdateGuestRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.GuestResponse;
import com.aurora.pms.service.GuestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/guests")
@Tag(name = "Guests", description = "Huespedes del hotel")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class GuestController {

	private final GuestService guestService;

	public GuestController(GuestService guestService) {
		this.guestService = guestService;
	}

	@GetMapping
	@Operation(summary = "List guests")
	@ApiResponse(responseCode = "200", description = "Guests found")
	public ResponseEntity<List<GuestResponse>> findAll() {
		return ResponseEntity.ok(guestService.findAll());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a guest by id")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Guest found"),
			@ApiResponse(responseCode = "400", description = "Invalid id",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Guest not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<GuestResponse> findById(@PathVariable UUID id) {
		return ResponseEntity.ok(guestService.findById(id));
	}

	@PostMapping
	@Operation(summary = "Create a guest")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Guest created"),
			@ApiResponse(responseCode = "400", description = "Invalid request",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<GuestResponse> create(@Valid @RequestBody CreateGuestRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(guestService.create(request));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update a guest")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Guest updated"),
			@ApiResponse(responseCode = "400", description = "Invalid request",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Guest not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<GuestResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateGuestRequest request) {
		return ResponseEntity.ok(guestService.update(id, request));
	}
}
