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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.CreateServiceRequestRequest;
import com.aurora.pms.dto.request.UpdateServiceRequestStatusRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.ServiceRequestResponse;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;
import com.aurora.pms.service.ServiceRequestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/service-requests")
@Tag(name = "Service Requests", description = "Solicitudes operativas generales, mantenimiento y desperfectos")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class ServiceRequestController {

	private final ServiceRequestService serviceRequestService;

	public ServiceRequestController(ServiceRequestService serviceRequestService) {
		this.serviceRequestService = serviceRequestService;
	}

	@GetMapping
	@Operation(summary = "List service requests")
	public ResponseEntity<List<ServiceRequestResponse>> findAll(
			@Parameter(description = "Filter by type") @RequestParam(required = false) ServiceRequestType type,
			@Parameter(description = "Filter by booking id") @RequestParam(required = false) UUID bookingId,
			@Parameter(description = "Filter by room id") @RequestParam(required = false) UUID roomId,
			@Parameter(description = "Filter by status") @RequestParam(required = false) ServiceRequestStatus status
	) {
		return ResponseEntity.ok(serviceRequestService.findAll(type, bookingId, roomId, status));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a service request")
	public ResponseEntity<ServiceRequestResponse> findById(@PathVariable UUID id) {
		return ResponseEntity.ok(serviceRequestService.findById(id));
	}

	@PostMapping
	@Operation(summary = "Create a service request")
	public ResponseEntity<ServiceRequestResponse> create(@Valid @RequestBody CreateServiceRequestRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(serviceRequestService.create(request));
	}

	@PostMapping("/{id}/status")
	@Operation(summary = "Change service request status")
	public ResponseEntity<ServiceRequestResponse> updateStatus(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateServiceRequestStatusRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		String actorEmail = currentUser != null ? currentUser.getUsername() : null;
		return ResponseEntity.ok(serviceRequestService.updateStatus(id, request, actorEmail));
	}
}
