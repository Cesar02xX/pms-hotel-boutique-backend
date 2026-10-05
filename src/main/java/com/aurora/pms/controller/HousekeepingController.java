package com.aurora.pms.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.CreateStayoverCleaningRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.HousekeepingRoomResponse;
import com.aurora.pms.dto.response.StayoverCleaningResponse;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.service.HousekeepingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/housekeeping/rooms")
@Tag(name = "Housekeeping", description = "Operacion de limpieza de habitaciones")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class HousekeepingController {

	private final HousekeepingService housekeepingService;

	public HousekeepingController(HousekeepingService housekeepingService) {
		this.housekeepingService = housekeepingService;
	}

	@GetMapping
	@Operation(summary = "List rooms for housekeeping")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Rooms found"),
			@ApiResponse(responseCode = "400", description = "Invalid housekeeping status",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<HousekeepingRoomResponse>> findAll(
			@RequestParam(required = false) RoomHousekeepingStatus housekeepingStatus
	) {
		return ResponseEntity.ok(housekeepingService.findAll(housekeepingStatus));
	}

	@GetMapping("/{roomId}")
	@Operation(summary = "Get a room for housekeeping")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Room found"),
			@ApiResponse(responseCode = "400", description = "Invalid room id",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Room not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<HousekeepingRoomResponse> findById(@PathVariable UUID roomId) {
		return ResponseEntity.ok(housekeepingService.findById(roomId));
	}

	@PostMapping("/{roomId}/start")
	@Operation(summary = "Start room cleaning")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Cleaning started"),
			@ApiResponse(responseCode = "400", description = "Invalid housekeeping transition",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Room not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<HousekeepingRoomResponse> startCleaning(
			@PathVariable UUID roomId,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		return ResponseEntity.ok(housekeepingService.startCleaning(roomId, actorEmail(currentUser)));
	}

	@PostMapping("/{roomId}/complete")
	@Operation(summary = "Complete room cleaning")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Cleaning completed"),
			@ApiResponse(responseCode = "400", description = "Invalid housekeeping transition",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Room not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<HousekeepingRoomResponse> completeCleaning(
			@PathVariable UUID roomId,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		return ResponseEntity.ok(housekeepingService.completeCleaning(roomId, actorEmail(currentUser)));
	}

	@PostMapping("/{roomId}/inspect")
	@Operation(summary = "Inspect a clean room")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Room inspected"),
			@ApiResponse(responseCode = "400", description = "Invalid housekeeping transition",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Room not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<HousekeepingRoomResponse> inspect(
			@PathVariable UUID roomId,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		return ResponseEntity.ok(housekeepingService.inspect(roomId, actorEmail(currentUser)));
	}

	@GetMapping("/stayover-cleanings")
	@Operation(summary = "List stayover cleanings, optionally filtered by booking and status")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Stayover cleanings found"),
			@ApiResponse(responseCode = "400", description = "Invalid booking id or status",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Booking not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<StayoverCleaningResponse>> findStayoverCleanings(
			@RequestParam(required = false) UUID bookingId,
			@RequestParam(required = false) ServiceRequestStatus status
	) {
		return ResponseEntity.ok(housekeepingService.findStayoverCleanings(bookingId, status));
	}

	@PostMapping("/{roomId}/stayover-cleanings")
	@Operation(summary = "Create a stayover cleaning task")
	public ResponseEntity<StayoverCleaningResponse> createStayoverCleaning(
			@PathVariable UUID roomId,
			@Valid @RequestBody CreateStayoverCleaningRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(housekeepingService.createStayoverCleaning(
				roomId,
				request.bookingId(),
				request.description(),
				actorEmail(currentUser)
		));
	}

	@PostMapping("/stayover-cleanings/{requestId}/start")
	@Operation(summary = "Start a stayover cleaning task")
	public ResponseEntity<StayoverCleaningResponse> startStayoverCleaning(
			@PathVariable UUID requestId,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		return ResponseEntity.ok(housekeepingService.startStayoverCleaning(requestId, actorEmail(currentUser)));
	}

	@PostMapping("/stayover-cleanings/{requestId}/complete")
	@Operation(summary = "Complete a stayover cleaning task")
	public ResponseEntity<StayoverCleaningResponse> completeStayoverCleaning(
			@PathVariable UUID requestId,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		return ResponseEntity.ok(housekeepingService.completeStayoverCleaning(requestId, actorEmail(currentUser)));
	}

	private static String actorEmail(UserDetails currentUser) {
		return currentUser != null ? currentUser.getUsername() : null;
	}
}
