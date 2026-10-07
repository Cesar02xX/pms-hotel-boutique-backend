package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

import com.aurora.pms.dto.response.MediaImageResponse;

/** lowStock se calcula en backend: currentQuantity <= minimumQuantity. */
public record InventoryItemResponse(
		UUID id,
		String sku,
		String name,
		String description,
		String category,
		String unit,
		Integer currentQuantity,
		Integer minimumQuantity,
		boolean lowStock,
		UUID productId,
		Boolean active,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt,
		List<MediaImageResponse> images
) {
}
