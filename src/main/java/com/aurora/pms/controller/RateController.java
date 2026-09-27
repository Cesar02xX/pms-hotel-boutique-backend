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

import com.aurora.pms.dto.request.CreateRateRequest;
import com.aurora.pms.dto.request.UpdateRateRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.RateResponse;
import com.aurora.pms.service.RateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/rates")
@Tag(name = "Rates", description = "Tarifas por tipo de habitación y vigencia")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class RateController {

	private final RateService rateService;

	public RateController(RateService rateService) {
		this.rateService = rateService;
	}

	@GetMapping
	@Operation(summary = "List rates")
	@ApiResponse(responseCode = "200", description = "Rates found")
	public ResponseEntity<List<RateResponse>> findAll() {
		return ResponseEntity.ok(rateService.findAll());
	}

	@PostMapping
	@Operation(summary = "Create a rate", description = "Prices are expressed in cents (priceCents)")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Rate created"),
			@ApiResponse(responseCode = "400", description = "Invalid request, date range or room type not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<RateResponse> create(@Valid @RequestBody CreateRateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(rateService.create(request));
	}

	@PutMapping("/{id}")
	@Operation(summary = "Update a rate", description = "Only the fields present in the body are updated")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Rate updated"),
			@ApiResponse(responseCode = "400", description = "Invalid request, date range or room type not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Rate not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<RateResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateRateRequest request) {
		return ResponseEntity.ok(rateService.update(id, request));
	}
}
