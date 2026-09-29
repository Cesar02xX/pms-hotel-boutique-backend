package com.aurora.pms.dto.request;

import java.util.UUID;

import com.aurora.pms.model.enums.ChargeCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateChargeRequest(
		@NotBlank(message = "Description is required")
		String description,

		@NotNull(message = "Quantity is required")
		@Positive(message = "Quantity must be greater than 0")
		Integer quantity,

		@NotNull(message = "Unit price is required")
		@PositiveOrZero(message = "Unit price must be greater than or equal to 0")
		Long unitPriceCents,

		@NotNull(message = "Category is required")
		ChargeCategory category,

		UUID productId
) {
}
