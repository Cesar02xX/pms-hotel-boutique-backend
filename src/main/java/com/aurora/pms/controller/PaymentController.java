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

import com.aurora.pms.dto.request.CreatePaymentRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.PaymentResponse;
import com.aurora.pms.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/payments")
@Tag(name = "Payments", description = "Pagos registrados en una reserva")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class PaymentController {

	private final PaymentService paymentService;

	public PaymentController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@GetMapping
	@Operation(summary = "List booking payments")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Payments found"),
			@ApiResponse(responseCode = "404", description = "Booking not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<PaymentResponse>> findAll(@PathVariable UUID bookingId) {
		return ResponseEntity.ok(paymentService.findAllByBookingId(bookingId));
	}

	@PostMapping
	@Operation(summary = "Register a payment",
			description = "The payment is recorded as completed. status, currency, paidAt, createdAt and "
					+ "processedByUser are set by the server. If the booking has an open guest folio, the payment "
					+ "reduces its balance; otherwise it is counted when the folio is opened.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Payment registered"),
			@ApiResponse(responseCode = "400", description = "Invalid request or guest folio closed",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Booking not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<PaymentResponse> create(
			@PathVariable UUID bookingId,
			@Valid @RequestBody CreatePaymentRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		String actorEmail = currentUser != null ? currentUser.getUsername() : null;
		return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(bookingId, request, actorEmail));
	}
}
