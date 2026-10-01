package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.model.InventoryItem;
import com.aurora.pms.model.InventoryMovement;
import com.aurora.pms.model.Order;
import com.aurora.pms.model.OrderItem;
import com.aurora.pms.model.Product;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.InventoryMovementReason;
import com.aurora.pms.model.enums.InventoryMovementType;
import com.aurora.pms.repository.InventoryItemRepository;
import com.aurora.pms.repository.InventoryMovementRepository;

/**
 * Descuento y devolución de inventario de un pedido de Room Service. Debe
 * usarse dentro de la transacción que cambia el estado del pedido, con el
 * pedido ya bloqueado. Los artículos se bloquean siempre en el mismo orden
 * (por id) para que dos pedidos simultáneos no se bloqueen entre sí.
 */
@Component
public class RoomServiceOrderInventory {

	private final InventoryItemRepository inventoryItemRepository;
	private final InventoryMovementRepository inventoryMovementRepository;
	private final InventoryStockLedger stockLedger;

	public RoomServiceOrderInventory(
			InventoryItemRepository inventoryItemRepository,
			InventoryMovementRepository inventoryMovementRepository,
			InventoryStockLedger stockLedger
	) {
		this.inventoryItemRepository = inventoryItemRepository;
		this.inventoryMovementRepository = inventoryMovementRepository;
		this.stockLedger = stockLedger;
	}

	/**
	 * Valida la existencia de todas las líneas y solo entonces descuenta cada
	 * una con un movimiento out/sale ligado al pedido. Si una línea falla no se
	 * descuenta ninguna.
	 */
	public void deduct(Order order, List<OrderItem> items, User actor) {
		if (order.getInventoryDeductedAt() != null) {
			return;
		}

		Map<UUID, OrderItem> itemsByInventoryItemId = new TreeMap<>();
		for (OrderItem item : items) {
			itemsByInventoryItemId.put(findSingleActiveInventoryItemId(item.getProduct()), item);
		}

		Map<UUID, InventoryItem> lockedItems = new TreeMap<>();
		itemsByInventoryItemId.forEach((inventoryItemId, item) -> {
			InventoryItem locked = lock(inventoryItemId);
			Product product = item.getProduct();
			if (!Boolean.TRUE.equals(locked.getActive())) {
				throw new BadRequestException("Inventory item for product " + product.getName() + " is inactive");
			}
			if (item.getQuantity() > locked.getCurrentQuantity()) {
				throw new BadRequestException("Insufficient stock for product " + product.getName()
						+ ": available " + locked.getCurrentQuantity() + ", requested " + item.getQuantity());
			}
			lockedItems.put(inventoryItemId, locked);
		});

		lockedItems.forEach((inventoryItemId, locked) -> stockLedger.apply(locked, movement(
				order, InventoryMovementType.out, InventoryMovementReason.sale,
				itemsByInventoryItemId.get(inventoryItemId).getQuantity(), actor, "accepted")));
		order.setInventoryDeductedAt(OffsetDateTime.now());
	}

	/**
	 * Devuelve exactamente lo descontado al aceptar, a partir de los
	 * movimientos de salida del pedido. No hace nada si el pedido nunca
	 * descontó inventario o ya fue restaurado.
	 */
	public void restore(Order order, User actor) {
		if (order.getInventoryDeductedAt() == null || order.getInventoryRestoredAt() != null) {
			return;
		}

		List<InventoryMovement> deductions = inventoryMovementRepository
				.findByRoomServiceOrderIdAndType(order.getId(), InventoryMovementType.out).stream()
				.sorted(Comparator.comparing(movement -> movement.getInventoryItem().getId()))
				.toList();
		for (InventoryMovement deduction : deductions) {
			InventoryItem locked = lock(deduction.getInventoryItem().getId());
			stockLedger.apply(locked, movement(
					order, InventoryMovementType.in, InventoryMovementReason.room_service_return,
					deduction.getQuantity(), actor, "cancelled"));
		}
		order.setInventoryRestoredAt(OffsetDateTime.now());
	}

	private UUID findSingleActiveInventoryItemId(Product product) {
		List<UUID> candidates = inventoryItemRepository.findActiveIdsByProductId(product.getId());
		if (candidates.size() != 1) {
			throw new BadRequestException("Product " + product.getName()
					+ " must have exactly one active inventory item; found " + candidates.size());
		}
		return candidates.get(0);
	}

	private InventoryItem lock(UUID inventoryItemId) {
		return inventoryItemRepository.findByIdForUpdate(inventoryItemId)
				.orElseThrow(() -> new IllegalStateException("Inventory item disappeared: " + inventoryItemId));
	}

	private static InventoryMovement movement(
			Order order,
			InventoryMovementType type,
			InventoryMovementReason reason,
			int quantity,
			User actor,
			String event
	) {
		InventoryMovement movement = new InventoryMovement();
		movement.setType(type);
		movement.setReason(reason);
		movement.setQuantity(quantity);
		movement.setRoomServiceOrder(order);
		movement.setResponsibleUser(actor);
		movement.setNotes("Room service order " + order.getId() + " " + event);
		return movement;
	}
}
