package com.aurora.pms.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.CreateBookingCompanionRequest;
import com.aurora.pms.dto.request.UpdateBookingCompanionRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.BookingCompanionResponse;
import com.aurora.pms.service.BookingCompanionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/companions")
@Tag(name = "Booking Companions", description = "Acompanantes asociados a reservas")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class BookingCompanionController {

	private final BookingCompanionService companionService;

	public BookingCompanionController(BookingCompanionService companionService) {
		this.companionService = companionService;
	}

	@GetMapping
	@Operation(summary = "List booking companions")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Companions found"),
			@ApiResponse(responseCode = "404", description = "Booking not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<BookingCompanionResponse>> findAll(@PathVariable UUID bookingId) {
		return ResponseEntity.ok(companionService.findAllByBookingId(bookingId));
	}

	@PostMapping
	@Operation(summary = "Create a booking companion")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Companion created"),
			@ApiResponse(responseCode = "400", description = "Invalid request or booking composition",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Booking not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<BookingCompanionResponse> create(
			@PathVariable UUID bookingId,
			@Valid @RequestBody CreateBookingCompanionRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(companionService.create(bookingId, request));
	}

	@PutMapping("/{companionId}")
	@Operation(summary = "Update a booking companion")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Companion updated"),
			@ApiResponse(responseCode = "400", description = "Invalid request or booking composition",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Booking or companion not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<BookingCompanionResponse> update(
			@PathVariable UUID bookingId,
			@PathVariable UUID companionId,
			@Valid @RequestBody UpdateBookingCompanionRequest request
	) {
		return ResponseEntity.ok(companionService.update(bookingId, companionId, request));
	}

	@DeleteMapping("/{companionId}")
	@Operation(summary = "Delete a booking companion")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Companion deleted"),
			@ApiResponse(responseCode = "404", description = "Booking or companion not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<Void> delete(@PathVariable UUID bookingId, @PathVariable UUID companionId) {
		companionService.delete(bookingId, companionId);
		return ResponseEntity.noContent().build();
	}
}
