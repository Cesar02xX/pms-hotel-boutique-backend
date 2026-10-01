package com.aurora.pms.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpsertInventoryItemRequest(
		@NotBlank String sku,
		@NotBlank String name,
		String description,
		@NotBlank String category,
		@NotBlank String unit,
		@Min(0) Integer minimumQuantity,
		UUID productId,
		Boolean active
) {
}
