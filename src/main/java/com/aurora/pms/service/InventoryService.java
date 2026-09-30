package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreateInventoryMovementRequest;
import com.aurora.pms.dto.response.InventoryItemResponse;
import com.aurora.pms.dto.response.InventoryMovementResponse;

public interface InventoryService {

	List<InventoryItemResponse> findItems(Boolean active, String category, Boolean lowStock);

	InventoryItemResponse findItem(UUID itemId);

	List<InventoryMovementResponse> findMovements(UUID itemId);

	InventoryMovementResponse createMovement(UUID itemId, CreateInventoryMovementRequest request, String actorEmail);
}
