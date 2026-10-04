package com.aurora.pms.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.CreateConciergeRequestRequest;
import com.aurora.pms.dto.request.UpdateConciergeRequestRequest;
import com.aurora.pms.dto.request.UpdateConciergeRequestStatusRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.ConciergeRequestResponse;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.service.ConciergeRequestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/concierge/requests")
@Tag(name = "Concierge", description = "Solicitudes de conserjería (ServiceRequest de tipo concierge)")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class ConciergeRequestController {

	private final ConciergeRequestService conciergeRequestService;

	public ConciergeRequestController(ConciergeRequestService conciergeRequestService) {
		this.conciergeRequestService = conciergeRequestService;
	}

	@GetMapping
	@Operation(summary = "List concierge requests",
			description = "Only requests of type concierge. Optional filters by booking and status.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Concierge requests found"),
			@ApiResponse(responseCode = "400", description = "Invalid filter value",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<ConciergeRequestResponse>> findAll(
			@Parameter(description = "Filter by booking id") @RequestParam(required = false) UUID bookingId,
			@Parameter(description = "Filter by status") @RequestParam(required = false) ServiceRequestStatus status
	) {
		return ResponseEntity.ok(conciergeRequestService.findAll(bookingId, status));
	}

	@GetMapping("/{requestId}")
	@Operation(summary = "Get a concierge request")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Concierge request found"),
			@ApiResponse(responseCode = "404", description = "Not found or not a concierge request",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<ConciergeRequestResponse> findById(@PathVariable UUID requestId) {
		return ResponseEntity.ok(conciergeRequestService.findById(requestId));
	}

	@PostMapping
	@Operation(summary = "Create a concierge request",
			description = "type (concierge), status (pending), room, guest and timestamps are set by the server. "
					+ "Room and guest are taken from the booking. No charge is created.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Concierge request created"),
			@ApiResponse(responseCode = "400",
					description = "Invalid request, booking not found or booking status does not allow requests",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<ConciergeRequestResponse> create(
			@Valid @RequestBody CreateConciergeRequestRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(conciergeRequestService.create(request));
	}

	@PostMapping("/{requestId}/status")
	@Operation(summary = "Change the status of a concierge request",
			description = "Allowed: pending -> accepted | rejected | cancelled, accepted -> in_progress | cancelled, "
					+ "in_progress -> completed | cancelled. completed, rejected and cancelled are terminal. "
					+ "Optional notes are appended to the existing notes. Without responsibleUserId, accepting, "
					+ "starting or completing an unassigned request assigns the authenticated user.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Status changed"),
			@ApiResponse(responseCode = "400", description = "Invalid request or status transition",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Not found or not a concierge request",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<ConciergeRequestResponse> updateStatus(
			@PathVariable UUID requestId,
			@Valid @RequestBody UpdateConciergeRequestStatusRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		String actorEmail = currentUser != null ? currentUser.getUsername() : null;
		return ResponseEntity.ok(conciergeRequestService.updateStatus(requestId, request, actorEmail));
	}

	@PutMapping("/{requestId}")
	@Operation(summary = "Edit a concierge request",
			description = "Notes can be replaced while the request is pending, accepted or in_progress; "
					+ "the description only while pending. Terminal requests cannot be edited.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Concierge request updated"),
			@ApiResponse(responseCode = "400",
					description = "Invalid request, terminal request or description change after pending",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Not found or not a concierge request",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<ConciergeRequestResponse> update(
			@PathVariable UUID requestId,
			@Valid @RequestBody UpdateConciergeRequestRequest request
	) {
		return ResponseEntity.ok(conciergeRequestService.update(requestId, request));
	}
}
