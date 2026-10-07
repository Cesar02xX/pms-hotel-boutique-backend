package com.aurora.pms.mapper;

import org.springframework.stereotype.Component;

import java.util.List;

import com.aurora.pms.dto.request.CreateInventoryMovementRequest;
import com.aurora.pms.dto.response.InventoryItemResponse;
import com.aurora.pms.dto.response.InventoryMovementResponse;
import com.aurora.pms.dto.response.MediaImageResponse;
import com.aurora.pms.model.InventoryItem;
import com.aurora.pms.model.InventoryMovement;

@Component
public class InventoryMapper {

	public InventoryItemResponse toResponse(InventoryItem item) {
		return toResponse(item, List.of());
	}

	public InventoryItemResponse toResponse(InventoryItem item, List<MediaImageResponse> images) {
		return new InventoryItemResponse(
				item.getId(),
				item.getSku(),
				item.getName(),
				item.getDescription(),
				item.getCategory(),
				item.getUnit(),
				item.getCurrentQuantity(),
				item.getMinimumQuantity(),
				item.getCurrentQuantity() <= item.getMinimumQuantity(),
				item.getProduct() != null ? item.getProduct().getId() : null,
				item.getActive(),
				item.getCreatedAt(),
				item.getUpdatedAt(),
				List.copyOf(images)
		);
	}

	public InventoryMovementResponse toResponse(InventoryMovement movement) {
		return new InventoryMovementResponse(
				movement.getId(),
				movement.getInventoryItem().getId(),
				movement.getType(),
				movement.getReason(),
				movement.getQuantity(),
				movement.getResponsibleUser() != null ? movement.getResponsibleUser().getId() : null,
				movement.getOccurredAt(),
				movement.getNotes(),
				movement.getCreatedAt(),
				movement.getRoomServiceOrder() != null ? movement.getRoomServiceOrder().getId() : null
		);
	}

	public InventoryMovement toEntity(CreateInventoryMovementRequest request, InventoryItem item) {
		InventoryMovement movement = new InventoryMovement();
		movement.setInventoryItem(item);
		movement.setType(request.type());
		movement.setReason(request.reason());
		movement.setQuantity(request.quantity());
		movement.setNotes(trimToNull(request.notes()));
		return movement;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
