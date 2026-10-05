package com.aurora.pms.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.PublicCreateBookingRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.PublicAvailabilityResponse;
import com.aurora.pms.dto.response.PublicBookingResponse;
import com.aurora.pms.dto.response.PublicRateResponse;
import com.aurora.pms.dto.response.PublicRoomTypeResponse;
import com.aurora.pms.service.PublicBookingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Contratos de la web pública. No requieren JWT: SecurityConfig abre solo estas
 * cuatro rutas con su método exacto.
 */
@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "Public Booking", description = "Búsqueda, disponibilidad y reserva desde la web pública, sin sesión")
@SecurityRequirements
public class PublicBookingController {

	private final PublicBookingService publicBookingService;

	public PublicBookingController(PublicBookingService publicBookingService) {
		this.publicBookingService = publicBookingService;
	}

	@GetMapping("/room-types")
	@Operation(summary = "List active room types for the public website")
	@ApiResponse(responseCode = "200", description = "Active room types found")
	public ResponseEntity<List<PublicRoomTypeResponse>> findRoomTypes() {
		return ResponseEntity.ok(publicBookingService.findRoomTypes());
	}

	@GetMapping("/rates")
	@Operation(
			summary = "List current public rates",
			description = "Active rates of active room types that have not expired in the hotel time zone."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Rates found"),
			@ApiResponse(responseCode = "400", description = "Invalid room type id",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<PublicRateResponse>> findRates(
			@Parameter(description = "Filter by room type id") @RequestParam(required = false) UUID roomTypeId
	) {
		return ResponseEntity.ok(publicBookingService.findRates(roomTypeId));
	}

	@GetMapping("/availability")
	@Operation(
			summary = "Check room type availability for a stay",
			description = "checkIn, checkOut and adults are required. checkIn cannot be in the past (hotel time "
					+ "zone) and the stay cannot exceed 30 nights. Only room types with capacity for the guests, "
					+ "an applicable rate and at least one free room are returned."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Availability calculated; results may be empty"),
			@ApiResponse(responseCode = "400", description = "Missing or invalid parameters, or room type not available",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<PublicAvailabilityResponse> findAvailability(
			@Parameter(description = "Check-in date (YYYY-MM-DD)", required = true)
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
			@Parameter(description = "Check-out date (YYYY-MM-DD)", required = true)
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
			@Parameter(description = "Number of adults (1 or more)", required = true)
			@RequestParam(required = false) Integer adults,
			@Parameter(description = "Number of children (defaults to 0)")
			@RequestParam(required = false) Integer children,
			@Parameter(description = "Restrict the search to one room type")
			@RequestParam(required = false) UUID roomTypeId
	) {
		return ResponseEntity.ok(publicBookingService.findAvailability(checkIn, checkOut, adults, children, roomTypeId));
	}

	@PostMapping("/bookings")
	@Operation(
			summary = "Create a public booking",
			description = "Creates the guest (or reuses it when both email and document match the same guest) "
					+ "and a pending booking without an assigned room. The rate, status and amounts are chosen "
					+ "by the server; rateId, roomId and status sent by the client are ignored."
	)
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Booking created as pending"),
			@ApiResponse(responseCode = "400", description = "Invalid request, room type not available, "
					+ "capacity exceeded or no applicable rate",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "No availability for the dates, or guest details "
					+ "conflict with an existing guest",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<PublicBookingResponse> create(@Valid @RequestBody PublicCreateBookingRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(publicBookingService.create(request));
	}
}
