package com.aurora.pms.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.InventoryItem;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from InventoryItem i where i.id = :id")
	Optional<InventoryItem> findByIdForUpdate(@Param("id") UUID id);

	/**
	 * Filtros opcionales: un parámetro null no filtra. category se compara en
	 * minúsculas (el servicio la normaliza). lowStock=true devuelve
	 * currentQuantity <= minimumQuantity; lowStock=false, el resto.
	 */
	@Query("""
			select i from InventoryItem i
			where (:active is null or i.active = :active)
			  and (:category is null or lower(i.category) = :category)
			  and (:lowStock is null
			       or (:lowStock = true and i.currentQuantity <= i.minimumQuantity)
			       or (:lowStock = false and i.currentQuantity > i.minimumQuantity))
			order by i.name asc, i.sku asc
			""")
	List<InventoryItem> search(
			@Param("active") Boolean active,
			@Param("category") String category,
			@Param("lowStock") Boolean lowStock
	);
}
