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

import com.aurora.pms.dto.request.CreateChargeRequest;
import com.aurora.pms.dto.request.VoidChargeRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.ChargeResponse;
import com.aurora.pms.dto.response.GuestFolioResponse;
import com.aurora.pms.service.GuestFolioService;
import com.aurora.pms.service.GuestFolioService.OpenFolioResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}")
@Tag(name = "Guest Folio", description = "Folio (cuenta) del huesped y sus cargos")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class GuestFolioController {

	private final GuestFolioService folioService;

	public GuestFolioController(GuestFolioService folioService) {
		this.folioService = folioService;
	}

	@GetMapping("/folio")
	@Operation(summary = "Get the guest folio of a booking", description = "Returns the account, its balance and its charges.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Folio found"),
			@ApiResponse(responseCode = "404", description = "Booking or guest account not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<GuestFolioResponse> getFolio(@PathVariable UUID bookingId) {
		return ResponseEntity.ok(folioService.getFolio(bookingId));
	}

	@PostMapping("/folio/open")
	@Operation(summary = "Open the guest folio of a booking",
			description = "Idempotent: returns 201 when the account is created and 200 with the existing account "
					+ "when the booking already has one. The account is linked to the booking's primary guest.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Folio opened"),
			@ApiResponse(responseCode = "200", description = "Folio already existed; returned unchanged"),
			@ApiResponse(responseCode = "400", description = "Booking status does not allow opening a folio",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Booking not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<GuestFolioResponse> openFolio(@PathVariable UUID bookingId) {
		OpenFolioResult result = folioService.openFolio(bookingId);
		HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(result.folio());
	}

	@GetMapping("/charges")
	@Operation(summary = "List the charges of a booking", description = "Includes voided charges.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Charges found"),
			@ApiResponse(responseCode = "404", description = "Booking not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<ChargeResponse>> findCharges(@PathVariable UUID bookingId) {
		return ResponseEntity.ok(folioService.findCharges(bookingId));
	}

	@PostMapping("/charges")
	@Operation(summary = "Post a charge to the guest folio",
			description = "amountCents, status, currency, timestamps and creator are set by the server.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Charge posted"),
			@ApiResponse(responseCode = "400", description = "Invalid request or guest account not open",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Booking, guest account or product not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<ChargeResponse> createCharge(
			@PathVariable UUID bookingId,
			@Valid @RequestBody CreateChargeRequest request,
			@Parameter(hidden = true) @AuthenticationPrincipal UserDetails currentUser
	) {
		String actorEmail = currentUser != null ? currentUser.getUsername() : null;
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(folioService.createCharge(bookingId, request, actorEmail));
	}

	@PostMapping("/charges/{chargeId}/void")
	@Operation(summary = "Void a charge", description = "Requires a reason. A voided charge no longer counts toward the balance.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Charge voided"),
			@ApiResponse(responseCode = "400", description = "Missing reason, charge already voided or guest account not open",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Booking, guest account or charge not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<ChargeResponse> voidCharge(
			@PathVariable UUID bookingId,
			@PathVariable UUID chargeId,
			@Valid @RequestBody VoidChargeRequest request
	) {
		return ResponseEntity.ok(folioService.voidCharge(bookingId, chargeId, request));
	}
}
