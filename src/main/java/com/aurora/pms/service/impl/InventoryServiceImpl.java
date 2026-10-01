package com.aurora.pms.service.impl;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateInventoryMovementRequest;
import com.aurora.pms.dto.response.InventoryItemResponse;
import com.aurora.pms.dto.response.InventoryMovementResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.InventoryMapper;
import com.aurora.pms.model.InventoryItem;
import com.aurora.pms.model.InventoryMovement;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.InventoryMovementReason;
import com.aurora.pms.model.enums.InventoryMovementType;
import com.aurora.pms.repository.InventoryItemRepository;
import com.aurora.pms.repository.InventoryMovementRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.service.InventoryService;

/**
 * Existencias sobre items ya creados. No toca Product.stockQuantity ni se
 * integra con Caja; los movimientos nunca se borran. Los movimientos
 * automáticos de Room Service (room_service_return) no se aceptan aquí.
 */
@Service
public class InventoryServiceImpl implements InventoryService {

	private static final Map<InventoryMovementType, Set<InventoryMovementReason>> REASONS_BY_TYPE = Map.of(
			InventoryMovementType.in, EnumSet.of(
					InventoryMovementReason.purchase,
					InventoryMovementReason.restock,
					InventoryMovementReason.physical_count),
			InventoryMovementType.out, EnumSet.of(
					InventoryMovementReason.consumption,
					InventoryMovementReason.sale,
					InventoryMovementReason.shrinkage,
					InventoryMovementReason.physical_count)
	);

	private final InventoryItemRepository inventoryItemRepository;
	private final InventoryMovementRepository inventoryMovementRepository;
	private final UserRepository userRepository;
	private final InventoryMapper inventoryMapper;
	private final InventoryStockLedger stockLedger;

	public InventoryServiceImpl(
			InventoryItemRepository inventoryItemRepository,
			InventoryMovementRepository inventoryMovementRepository,
			UserRepository userRepository,
			InventoryMapper inventoryMapper,
			InventoryStockLedger stockLedger
	) {
		this.inventoryItemRepository = inventoryItemRepository;
		this.inventoryMovementRepository = inventoryMovementRepository;
		this.userRepository = userRepository;
		this.inventoryMapper = inventoryMapper;
		this.stockLedger = stockLedger;
	}

	@Override
	@Transactional(readOnly = true)
	public List<InventoryItemResponse> findItems(Boolean active, String category, Boolean lowStock) {
		String normalizedCategory = category == null || category.isBlank()
				? null
				: category.trim().toLowerCase(Locale.ROOT);
		return inventoryItemRepository.search(active, normalizedCategory, lowStock).stream()
				.map(inventoryMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public InventoryItemResponse findItem(UUID itemId) {
		InventoryItem item = inventoryItemRepository.findById(itemId)
				.orElseThrow(() -> notFound(itemId));
		return inventoryMapper.toResponse(item);
	}

	@Override
	@Transactional(readOnly = true)
	public List<InventoryMovementResponse> findMovements(UUID itemId) {
		if (!inventoryItemRepository.existsById(itemId)) {
			throw notFound(itemId);
		}
		return inventoryMovementRepository.findByInventoryItemIdOrderByOccurredAtAscCreatedAtAsc(itemId).stream()
				.map(inventoryMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional
	public InventoryMovementResponse createMovement(
			UUID itemId,
			CreateInventoryMovementRequest request,
			String actorEmail
	) {
		// El bloqueo serializa los movimientos del item: dos salidas simultáneas
		// no pueden leer el mismo stock y dejarlo negativo o perder una resta.
		InventoryItem item = inventoryItemRepository.findByIdForUpdate(itemId)
				.orElseThrow(() -> notFound(itemId));
		if (!Boolean.TRUE.equals(item.getActive())) {
			throw new BadRequestException("Inventory item is inactive");
		}
		if (!REASONS_BY_TYPE.get(request.type()).contains(request.reason())) {
			throw new BadRequestException(
					"Reason " + request.reason() + " is not valid for movement type " + request.type());
		}

		InventoryMovement movement = inventoryMapper.toEntity(request, item);
		movement.setResponsibleUser(findActor(actorEmail));

		return inventoryMapper.toResponse(stockLedger.apply(item, movement));
	}

	private User findActor(String actorEmail) {
		if (actorEmail == null) {
			return null;
		}
		return userRepository.findByEmail(actorEmail).orElse(null);
	}

	private static ResourceNotFoundException notFound(UUID itemId) {
		return new ResourceNotFoundException("Inventory item not found: " + itemId);
	}
}
