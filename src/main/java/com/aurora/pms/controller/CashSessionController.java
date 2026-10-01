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
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.CloseCashSessionRequest;
import com.aurora.pms.dto.request.CreateCashMovementRequest;
import com.aurora.pms.dto.request.OpenCashSessionRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.CashMovementResponse;
import com.aurora.pms.dto.response.CashSessionResponse;
import com.aurora.pms.service.CashSessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/cash-sessions")
@Tag(name = "Cash", description = "Sesiones de caja y movimientos manuales")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class CashSessionController {

	private final CashSessionService cashSessionService;

	public CashSessionController(CashSessionService cashSessionService) {
		this.cashSessionService = cashSessionService;
	}

	@GetMapping("/current")
	@Operation(summary = "Get the open cash session",
			description = "Returns the authenticated user's open cash session. "
					+ "Totals and expectedBalanceCents are computed by the server.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Open cash session found"),
			@ApiResponse(responseCode = "404", description = "No open cash session",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<CashSessionResponse> findCurrent(
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		String actorEmail = currentUser != null ? currentUser.getUsername() : null;
		return ResponseEntity.ok(cashSessionService.findCurrent(actorEmail));
	}

	@PostMapping("/open")
	@Operation(summary = "Open a cash session",
			description = "id, status, currency, openedAt and openedByUser are set by the server. "
					+ "Each user can have only one open session at a time.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Cash session opened"),
			@ApiResponse(responseCode = "400", description = "Invalid request or a session is already open",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<CashSessionResponse> open(
			@Valid @RequestBody OpenCashSessionRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		String actorEmail = currentUser != null ? currentUser.getUsername() : null;
		return ResponseEntity.status(HttpStatus.CREATED).body(cashSessionService.open(request, actorEmail));
	}

	@PostMapping("/{id}/close")
	@Operation(summary = "Close a cash session",
			description = "The server computes expectedBalanceCents (opening + income - expense) and "
					+ "differenceCents (counted - expected). Notes are appended to the opening notes.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Cash session closed"),
			@ApiResponse(responseCode = "400", description = "Invalid request or session already closed",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Cash session not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<CashSessionResponse> close(
			@PathVariable UUID id,
			@Valid @RequestBody CloseCashSessionRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		String actorEmail = currentUser != null ? currentUser.getUsername() : null;
		return ResponseEntity.ok(cashSessionService.close(id, request, actorEmail));
	}

	@GetMapping("/{id}/movements")
	@Operation(summary = "List cash session movements")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Movements found"),
			@ApiResponse(responseCode = "404", description = "Cash session not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<CashMovementResponse>> findMovements(@PathVariable UUID id) {
		return ResponseEntity.ok(cashSessionService.findMovements(id));
	}

	@PostMapping("/{id}/movements")
	@Operation(summary = "Register a manual cash movement",
			description = "currency, occurredAt, createdAt and responsibleUser are set by the server. "
					+ "An expense cannot exceed the cash currently available in the session.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Movement registered"),
			@ApiResponse(responseCode = "400",
					description = "Invalid request, session closed or expense exceeds available cash",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Cash session not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<CashMovementResponse> createMovement(
			@PathVariable UUID id,
			@Valid @RequestBody CreateCashMovementRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		String actorEmail = currentUser != null ? currentUser.getUsername() : null;
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(cashSessionService.createMovement(id, request, actorEmail));
	}
}
