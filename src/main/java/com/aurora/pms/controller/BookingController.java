package com.aurora.pms.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.CreateBookingRequest;
import com.aurora.pms.dto.request.UpdateBookingRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.BookingResponse;
import com.aurora.pms.service.BookingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings", description = "Reservas del hotel")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class BookingController {

	private final BookingService bookingService;

	public BookingController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@GetMapping
	@Operation(summary = "List bookings")
	@ApiResponse(responseCode = "200", description = "Bookings found")
	public ResponseEntity<List<BookingResponse>> findAll() {
		return ResponseEntity.ok(bookingService.findAll());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a booking by id")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Booking found"),
			@ApiResponse(responseCode = "400", description = "Invalid id",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Booking not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<BookingResponse> findById(@PathVariable UUID id) {
		return ResponseEntity.ok(bookingService.findById(id));
	}

	@PostMapping
	@Operation(summary = "Create a booking")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Booking created"),
			@ApiResponse(responseCode = "400", description = "Invalid request or related resource",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update a booking",
			description = "Partial update: only non-null fields in the body are applied")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Booking updated"),
			@ApiResponse(responseCode = "400", description = "Invalid request or related resource",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Booking not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<BookingResponse> update(@PathVariable UUID id,
			@Valid @RequestBody UpdateBookingRequest request) {
		return ResponseEntity.ok(bookingService.update(id, request));
	}
}
