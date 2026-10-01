package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.InventoryMovementReason;
import com.aurora.pms.model.enums.InventoryMovementType;

public record InventoryMovementResponse(
		UUID id,
		UUID inventoryItemId,
		InventoryMovementType type,
		InventoryMovementReason reason,
		Integer quantity,
		UUID responsibleUserId,
		OffsetDateTime occurredAt,
		String notes,
		OffsetDateTime createdAt,
		UUID roomServiceOrderId
) {
}
