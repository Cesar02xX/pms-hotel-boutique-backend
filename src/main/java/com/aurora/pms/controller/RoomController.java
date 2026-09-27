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

import com.aurora.pms.dto.request.CreateRoomRequest;
import com.aurora.pms.dto.request.UpdateRoomRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.RoomResponse;
import com.aurora.pms.service.RoomService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/rooms")
@Tag(name = "Rooms", description = "Habitaciones físicas del hotel")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class RoomController {

	private final RoomService roomService;

	public RoomController(RoomService roomService) {
		this.roomService = roomService;
	}

	@GetMapping
	@Operation(summary = "List rooms")
	@ApiResponse(responseCode = "200", description = "Rooms found")
	public ResponseEntity<List<RoomResponse>> findAll() {
		return ResponseEntity.ok(roomService.findAll());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a room by id")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Room found"),
			@ApiResponse(responseCode = "400", description = "Invalid id",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Room not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<RoomResponse> findById(@PathVariable UUID id) {
		return ResponseEntity.ok(roomService.findById(id));
	}

	@PostMapping
	@Operation(summary = "Create a room")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Room created"),
			@ApiResponse(responseCode = "400", description = "Invalid request or room type not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<RoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(roomService.create(request));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update a room", description = "Only the fields present in the body are updated")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Room updated"),
			@ApiResponse(responseCode = "400", description = "Invalid request or room type not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Room not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<RoomResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateRoomRequest request) {
		return ResponseEntity.ok(roomService.update(id, request));
	}
}
