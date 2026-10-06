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

import com.aurora.pms.dto.request.CreateHousekeepingChecklistRequest;
import com.aurora.pms.dto.request.UpdateHousekeepingChecklistRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.HousekeepingChecklistResponse;
import com.aurora.pms.model.enums.HousekeepingChecklistStatus;
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
@RequestMapping("/api/v1/housekeeping/checklists")
@Tag(name = "Housekeeping", description = "Checklists reales de limpieza")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class HousekeepingChecklistController {

	private final HousekeepingService housekeepingService;

	public HousekeepingChecklistController(HousekeepingService housekeepingService) {
		this.housekeepingService = housekeepingService;
	}

	@GetMapping
	@Operation(summary = "List housekeeping checklists")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Checklists found"),
			@ApiResponse(responseCode = "400", description = "Invalid filter",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<HousekeepingChecklistResponse>> findChecklists(
			@RequestParam(required = false) UUID roomId,
			@RequestParam(required = false) HousekeepingChecklistStatus status,
			@RequestParam(required = false) UUID responsibleUserId
	) {
		return ResponseEntity.ok(housekeepingService.findChecklists(roomId, status, responsibleUserId));
	}

	@PostMapping
	@Operation(summary = "Create a housekeeping checklist from a real cleaning task")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Checklist created"),
			@ApiResponse(responseCode = "400", description = "Invalid payload",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Cleaning task not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "Duplicated checklist or invalid task state",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<HousekeepingChecklistResponse> createChecklist(
			@Valid @RequestBody CreateHousekeepingChecklistRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(housekeepingService.createChecklist(request, actorEmail(currentUser)));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update a housekeeping checklist")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Checklist updated"),
			@ApiResponse(responseCode = "400", description = "Invalid payload",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Checklist not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "Invalid checklist state",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<HousekeepingChecklistResponse> updateChecklist(
			@PathVariable UUID id,
			@Valid @RequestBody UpdateHousekeepingChecklistRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		return ResponseEntity.ok(housekeepingService.updateChecklist(id, request, actorEmail(currentUser)));
	}

	private static String actorEmail(UserDetails currentUser) {
		return currentUser != null ? currentUser.getUsername() : null;
	}
}
