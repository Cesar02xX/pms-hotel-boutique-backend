package com.aurora.pms.dto.response;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.model.enums.ProductCategory;

public record RoomServiceProductResponse(
		UUID id,
		String sku,
		String name,
		String description,
		ProductCategory category,
		Long priceCents,
		String currency,
		Boolean active,
		Integer stockQuantity,
		List<MediaImageResponse> images
) {
}
