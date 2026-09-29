package com.aurora.pms.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.CreateDepositRequest;
import com.aurora.pms.dto.request.RefundDepositRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.DepositResponse;
import com.aurora.pms.service.DepositService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/deposits")
@Tag(name = "Deposits", description = "Depositos de garantia de una reserva")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class DepositController {

	private final DepositService depositService;

	public DepositController(DepositService depositService) {
		this.depositService = depositService;
	}

	@GetMapping
	@Operation(summary = "List booking deposits")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Deposits found"),
			@ApiResponse(responseCode = "404", description = "Booking not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<DepositResponse>> findAll(@PathVariable UUID bookingId) {
		return ResponseEntity.ok(depositService.findAllByBookingId(bookingId));
	}

	@PostMapping
	@Operation(summary = "Register a deposit",
			description = "The deposit is held and linked to the booking's primary guest. status, currency and "
					+ "timestamps are set by the server.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Deposit registered"),
			@ApiResponse(responseCode = "400", description = "Invalid request",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Booking not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<DepositResponse> create(
			@PathVariable UUID bookingId,
			@Valid @RequestBody CreateDepositRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(depositService.create(bookingId, request));
	}

	@PostMapping("/{depositId}/refund")
	@Operation(summary = "Refund a held deposit",
			description = "Body is optional; a reason, if given, is appended to the deposit notes.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Deposit refunded"),
			@ApiResponse(responseCode = "400", description = "Deposit already refunded or applied",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Booking or deposit not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<DepositResponse> refund(
			@PathVariable UUID bookingId,
			@PathVariable UUID depositId,
			@Valid @RequestBody(required = false) RefundDepositRequest request
	) {
		return ResponseEntity.ok(depositService.refund(bookingId, depositId, request));
	}
}
