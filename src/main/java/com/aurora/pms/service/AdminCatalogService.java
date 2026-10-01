package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.UpsertAmenityRequest;
import com.aurora.pms.dto.request.UpsertInventoryItemRequest;
import com.aurora.pms.dto.request.UpsertProductRequest;
import com.aurora.pms.dto.request.UpsertPromotionRequest;
import com.aurora.pms.dto.response.AmenityResponse;
import com.aurora.pms.dto.response.InventoryItemResponse;
import com.aurora.pms.dto.response.PromotionResponse;
import com.aurora.pms.dto.response.RoomServiceProductResponse;

public interface AdminCatalogService {

	List<AmenityResponse> findAmenities(Boolean active);

	AmenityResponse findAmenity(UUID id);

	AmenityResponse createAmenity(UpsertAmenityRequest request);

	AmenityResponse updateAmenity(UUID id, UpsertAmenityRequest request);

	List<RoomServiceProductResponse> findProducts(Boolean active);

	RoomServiceProductResponse createProduct(UpsertProductRequest request);

	RoomServiceProductResponse updateProduct(UUID id, UpsertProductRequest request);

	InventoryItemResponse createInventoryItem(UpsertInventoryItemRequest request);

	InventoryItemResponse updateInventoryItem(UUID id, UpsertInventoryItemRequest request);

	List<PromotionResponse> findPromotions(Boolean active);

	PromotionResponse createPromotion(UpsertPromotionRequest request);

	PromotionResponse updatePromotion(UUID id, UpsertPromotionRequest request);
}
