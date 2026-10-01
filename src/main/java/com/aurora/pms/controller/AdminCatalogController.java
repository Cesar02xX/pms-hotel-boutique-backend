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

import com.aurora.pms.dto.request.UpsertAmenityRequest;
import com.aurora.pms.dto.request.UpsertInventoryItemRequest;
import com.aurora.pms.dto.request.UpsertProductRequest;
import com.aurora.pms.dto.request.UpsertPromotionRequest;
import com.aurora.pms.dto.response.AmenityResponse;
import com.aurora.pms.dto.response.InventoryItemResponse;
import com.aurora.pms.dto.response.PromotionResponse;
import com.aurora.pms.dto.response.RoomServiceProductResponse;
import com.aurora.pms.service.AdminCatalogService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {

	private final AdminCatalogService adminCatalogService;

	public AdminCatalogController(AdminCatalogService adminCatalogService) {
		this.adminCatalogService = adminCatalogService;
	}

	@GetMapping("/amenities")
	public ResponseEntity<List<AmenityResponse>> findAmenities(@RequestParam(required = false) Boolean active) {
		return ResponseEntity.ok(adminCatalogService.findAmenities(active));
	}

	@GetMapping("/amenities/{id}")
	public ResponseEntity<AmenityResponse> findAmenity(@PathVariable UUID id) {
		return ResponseEntity.ok(adminCatalogService.findAmenity(id));
	}

	@PostMapping("/amenities")
	public ResponseEntity<AmenityResponse> createAmenity(@Valid @RequestBody UpsertAmenityRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(adminCatalogService.createAmenity(request));
	}

	@PutMapping("/amenities/{id}")
	public ResponseEntity<AmenityResponse> updateAmenity(
			@PathVariable UUID id,
			@Valid @RequestBody UpsertAmenityRequest request
	) {
		return ResponseEntity.ok(adminCatalogService.updateAmenity(id, request));
	}

	@GetMapping("/room-service/products")
	public ResponseEntity<List<RoomServiceProductResponse>> findProducts(@RequestParam(required = false) Boolean active) {
		return ResponseEntity.ok(adminCatalogService.findProducts(active));
	}

	@PostMapping("/room-service/products")
	public ResponseEntity<RoomServiceProductResponse> createProduct(@Valid @RequestBody UpsertProductRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(adminCatalogService.createProduct(request));
	}

	@PutMapping("/room-service/products/{id}")
	public ResponseEntity<RoomServiceProductResponse> updateProduct(
			@PathVariable UUID id,
			@Valid @RequestBody UpsertProductRequest request
	) {
		return ResponseEntity.ok(adminCatalogService.updateProduct(id, request));
	}

	@PostMapping("/inventory/items")
	public ResponseEntity<InventoryItemResponse> createInventoryItem(
			@Valid @RequestBody UpsertInventoryItemRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(adminCatalogService.createInventoryItem(request));
	}

	@PutMapping("/inventory/items/{id}")
	public ResponseEntity<InventoryItemResponse> updateInventoryItem(
			@PathVariable UUID id,
			@Valid @RequestBody UpsertInventoryItemRequest request
	) {
		return ResponseEntity.ok(adminCatalogService.updateInventoryItem(id, request));
	}

	@GetMapping("/promotions")
	public ResponseEntity<List<PromotionResponse>> findPromotions(@RequestParam(required = false) Boolean active) {
		return ResponseEntity.ok(adminCatalogService.findPromotions(active));
	}

	@PostMapping("/promotions")
	public ResponseEntity<PromotionResponse> createPromotion(@Valid @RequestBody UpsertPromotionRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(adminCatalogService.createPromotion(request));
	}

	@PutMapping("/promotions/{id}")
	public ResponseEntity<PromotionResponse> updatePromotion(
			@PathVariable UUID id,
			@Valid @RequestBody UpsertPromotionRequest request
	) {
		return ResponseEntity.ok(adminCatalogService.updatePromotion(id, request));
	}
}
