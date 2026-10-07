package com.aurora.pms.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.response.PublicAmenityResponse;
import com.aurora.pms.service.AdminCatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/public/amenities")
@Tag(name = "Public Amenities", description = "Amenidades activas del hotel para la web pública, sin sesión")
@SecurityRequirements
public class PublicAmenityController {

	private final AdminCatalogService adminCatalogService;

	public PublicAmenityController(AdminCatalogService adminCatalogService) {
		this.adminCatalogService = adminCatalogService;
	}

	@GetMapping
	@Operation(summary = "List active amenities with their published images")
	@ApiResponse(responseCode = "200", description = "Active amenities found")
	public ResponseEntity<List<PublicAmenityResponse>> findAll() {
		return ResponseEntity.ok(adminCatalogService.findPublicAmenities());
	}
}
