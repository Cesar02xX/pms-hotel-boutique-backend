package com.aurora.pms.dto.request;

import com.aurora.pms.model.enums.InventoryMovementReason;
import com.aurora.pms.model.enums.InventoryMovementType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateInventoryMovementRequest(
		@NotNull(message = "Movement type is required")
		InventoryMovementType type,

		@NotNull(message = "Movement reason is required")
		InventoryMovementReason reason,

		@NotNull(message = "Quantity is required")
		@Positive(message = "Quantity must be greater than 0")
		@JsonDeserialize(using = WholeQuantityDeserializer.class)
		Integer quantity,

		String notes
) {
}
