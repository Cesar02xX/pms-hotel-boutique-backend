package com.aurora.pms.dto.request;

import java.util.UUID;
import java.util.List;

import jakarta.validation.Valid;
import com.aurora.pms.dto.request.MediaImageAssignmentRequest;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpsertInventoryItemRequest(
		String sku,
		@NotBlank String name,
		String description,
		@NotBlank String category,
		@NotBlank String unit,
		@Min(0) Integer minimumQuantity,
		UUID productId,
		Boolean active,
		List<@Valid MediaImageAssignmentRequest> images
) {
}
