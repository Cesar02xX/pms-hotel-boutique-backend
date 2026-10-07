package com.aurora.pms.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.MediaImage;

import jakarta.persistence.LockModeType;

public interface MediaImageRepository extends JpaRepository<MediaImage, UUID> {

	/** Bloquea las filas al asociarlas, para que dos registros no tomen la misma imagen a la vez. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<MediaImage> findByIdIn(Collection<UUID> ids);

	List<MediaImage> findByRoomTypeIdInOrderByPositionAsc(Collection<UUID> roomTypeIds);

	List<MediaImage> findByProductIdInOrderByPositionAsc(Collection<UUID> productIds);

	List<MediaImage> findByAmenityIdInOrderByPositionAsc(Collection<UUID> amenityIds);

	List<MediaImage> findByInventoryItemIdInOrderByPositionAsc(Collection<UUID> inventoryItemIds);

	List<MediaImage> findByPendingSinceBeforeOrderByPendingSinceAsc(OffsetDateTime threshold, Limit limit);

	/**
	 * Borra la fila solo si sigue pendiente. La fila queda bloqueada hasta el
	 * commit, así que una asociación concurrente espera y luego no la encuentra.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from MediaImage m where m.id = :id and m.pendingSince is not null and m.pendingSince < :threshold")
	int deleteIfPendingBefore(@Param("id") UUID id, @Param("threshold") OffsetDateTime threshold);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from MediaImage m where m.id = :id and m.pendingSince is not null")
	int deleteIfPending(@Param("id") UUID id);

	/**
	 * Una imagen es pública solo si está asociada a un registro activo. Una
	 * pendiente, o la de un registro desactivado, no se sirve sin sesión.
	 */
	@Query("""
			select (count(m) > 0) from MediaImage m
			where m.id = :id and (
				exists (select 1 from RoomType r where r.id = m.roomTypeId and r.active = true)
				or exists (select 1 from Product p where p.id = m.productId and p.active = true)
				or exists (select 1 from Amenity a where a.id = m.amenityId and a.active = true)
				or exists (select 1 from InventoryItem i where i.id = m.inventoryItemId and i.active = true)
			)
			""")
	boolean isPublic(@Param("id") UUID id);
}
