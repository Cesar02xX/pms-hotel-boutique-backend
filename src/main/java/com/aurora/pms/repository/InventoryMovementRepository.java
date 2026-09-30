package com.aurora.pms.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.InventoryMovement;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

	List<InventoryMovement> findByInventoryItemIdOrderByOccurredAtAscCreatedAtAsc(UUID inventoryItemId);
}
