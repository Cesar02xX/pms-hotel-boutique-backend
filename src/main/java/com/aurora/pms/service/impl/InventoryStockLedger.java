package com.aurora.pms.service.impl;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.model.InventoryItem;
import com.aurora.pms.model.InventoryMovement;
import com.aurora.pms.model.enums.InventoryMovementType;
import com.aurora.pms.repository.InventoryItemRepository;
import com.aurora.pms.repository.InventoryMovementRepository;

/**
 * Única vía para mover InventoryItem.currentQuantity: aplica el movimiento al
 * artículo y lo registra. El artículo debe llegar bloqueado
 * (findByIdForUpdate) y esto debe usarse dentro de la transacción de quien
 * registra el movimiento.
 */
@Component
public class InventoryStockLedger {

	private final InventoryItemRepository inventoryItemRepository;
	private final InventoryMovementRepository inventoryMovementRepository;
	private final Clock clock;

	public InventoryStockLedger(
			InventoryItemRepository inventoryItemRepository,
			InventoryMovementRepository inventoryMovementRepository,
			Clock clock
	) {
		this.inventoryItemRepository = inventoryItemRepository;
		this.inventoryMovementRepository = inventoryMovementRepository;
		this.clock = clock;
	}

	/**
	 * Aplica el tipo y la cantidad del movimiento al artículo bloqueado y
	 * guarda ambos. Una salida mayor que la existencia o una entrada que
	 * desborde el entero se rechazan (400) sin modificar nada.
	 */
	public InventoryMovement apply(InventoryItem lockedItem, InventoryMovement movement) {
		int newQuantity = calculateNewQuantity(
				lockedItem.getCurrentQuantity(), movement.getType(), movement.getQuantity());

		OffsetDateTime now = OffsetDateTime.now(clock);
		lockedItem.setCurrentQuantity(newQuantity);
		lockedItem.setUpdatedAt(now);
		inventoryItemRepository.save(lockedItem);

		movement.setInventoryItem(lockedItem);
		movement.setOccurredAt(now);
		movement.setCreatedAt(now);
		return inventoryMovementRepository.save(movement);
	}

	private static int calculateNewQuantity(int current, InventoryMovementType type, int quantity) {
		if (type == InventoryMovementType.out) {
			if (quantity > current) {
				throw new BadRequestException(
						"Insufficient stock: available " + current + ", requested " + quantity);
			}
			return current - quantity;
		}
		try {
			return Math.addExact(current, quantity);
		} catch (ArithmeticException exception) {
			throw new BadRequestException("Inventory quantity is too large");
		}
	}
}
