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

import com.aurora.pms.dto.request.SaveConciergeServiceRequest;
import com.aurora.pms.dto.response.ConciergeServiceResponse;
import com.aurora.pms.service.ConciergeCatalogService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/concierge/services")
public class ConciergeCatalogController {
	private final ConciergeCatalogService catalogService;

	public ConciergeCatalogController(ConciergeCatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping
	public ResponseEntity<List<ConciergeServiceResponse>> findAll(
			@RequestParam(defaultValue = "false") boolean activeOnly) {
		return ResponseEntity.ok(catalogService.findAll(activeOnly));
	}

	@PostMapping
	public ResponseEntity<ConciergeServiceResponse> create(@Valid @RequestBody SaveConciergeServiceRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.create(request));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ConciergeServiceResponse> update(@PathVariable UUID id,
			@Valid @RequestBody SaveConciergeServiceRequest request) {
		return ResponseEntity.ok(catalogService.update(id, request));
	}
}
