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

import com.aurora.pms.dto.request.CreateRoomTypeRequest;
import com.aurora.pms.dto.request.UpdateRoomTypeRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.RoomTypeResponse;
import com.aurora.pms.service.RoomTypeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/room-types")
@Tag(name = "Room Types", description = "Tipos de habitación y sus características")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class RoomTypeController {

	private final RoomTypeService roomTypeService;

	public RoomTypeController(RoomTypeService roomTypeService) {
		this.roomTypeService = roomTypeService;
	}

	@GetMapping
	@Operation(summary = "List room types")
	@ApiResponse(responseCode = "200", description = "Room types found")
	public ResponseEntity<List<RoomTypeResponse>> findAll() {
		return ResponseEntity.ok(roomTypeService.findAll());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a room type by id")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Room type found"),
			@ApiResponse(responseCode = "400", description = "Invalid id",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Room type not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<RoomTypeResponse> findById(@PathVariable UUID id) {
		return ResponseEntity.ok(roomTypeService.findById(id));
	}

	@PostMapping
	@Operation(summary = "Create a room type")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Room type created"),
			@ApiResponse(responseCode = "400", description = "Invalid request or room feature not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<RoomTypeResponse> create(@Valid @RequestBody CreateRoomTypeRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(roomTypeService.create(request));
	}

	@PutMapping("/{id}")
	@Operation(
			summary = "Update a room type",
			description = "Only the fields present in the body are updated. When roomFeatureIds is sent, "
					+ "it replaces the current features; an empty list removes them all."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Room type updated"),
			@ApiResponse(responseCode = "400", description = "Invalid request or room feature not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Room type not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<RoomTypeResponse> update(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateRoomTypeRequest request
	) {
		return ResponseEntity.ok(roomTypeService.update(id, request));
	}
}
