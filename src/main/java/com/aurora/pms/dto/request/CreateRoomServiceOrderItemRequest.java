package com.aurora.pms.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateRoomServiceOrderItemRequest(
		@NotNull(message = "Product id is required")
		UUID productId,

		@NotNull(message = "Quantity is required")
		@Positive(message = "Quantity must be greater than zero")
		Integer quantity
) {
}
