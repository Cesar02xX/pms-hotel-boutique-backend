package com.aurora.pms.dto.request;

import com.aurora.pms.model.enums.ProductCategory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpsertProductRequest(
		@NotBlank String sku,
		@NotBlank String name,
		String description,
		@NotNull ProductCategory category,
		@NotNull @Min(1) Long priceCents,
		@Min(0) Integer reorderLevel,
		Boolean active
) {
}
