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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.SaveHousekeepingServiceRequest;
import com.aurora.pms.dto.response.HousekeepingServiceOptionResponse;
import com.aurora.pms.service.HousekeepingCatalogService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/housekeeping/services")
public class HousekeepingCatalogController {
	private final HousekeepingCatalogService catalogService;

	public HousekeepingCatalogController(HousekeepingCatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping
	public ResponseEntity<List<HousekeepingServiceOptionResponse>> findAll(
			@RequestParam(defaultValue = "false") boolean activeOnly) {
		return ResponseEntity.ok(catalogService.findAll(activeOnly));
	}

	@PostMapping
	public ResponseEntity<HousekeepingServiceOptionResponse> create(
			@Valid @RequestBody SaveHousekeepingServiceRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.create(request));
	}

	@PutMapping("/{id}")
	public ResponseEntity<HousekeepingServiceOptionResponse> update(@PathVariable UUID id,
			@Valid @RequestBody SaveHousekeepingServiceRequest request) {
		return ResponseEntity.ok(catalogService.update(id, request));
	}
}
