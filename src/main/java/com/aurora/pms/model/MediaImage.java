package com.aurora.pms.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.MediaTarget;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metadatos de una imagen de catálogo. Los bytes viven en el almacenamiento
 * de objetos bajo claves derivadas del id; aquí solo queda a qué registro
 * pertenece, en qué posición y si es la principal.
 *
 * El id lo asigna el servicio antes de subir los archivos, para que las
 * claves del almacenamiento existan antes que la fila.
 */
@Entity
@Table(name = "media_images")
@Getter
@Setter
@NoArgsConstructor
public class MediaImage {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MediaTarget target;

	@Column(name = "room_type_id")
	private UUID roomTypeId;

	@Column(name = "product_id")
	private UUID productId;

	@Column(name = "amenity_id")
	private UUID amenityId;

	@Column(name = "inventory_item_id")
	private UUID inventoryItemId;

	private Integer position;

	@Column(name = "is_primary", nullable = false)
	private Boolean primary = false;

	@Column(name = "alt_text")
	private String altText;

	@Column(name = "original_content_type", nullable = false)
	private String originalContentType;

	@Column(name = "variant_content_type", nullable = false)
	private String variantContentType;

	@Column(name = "size_bytes", nullable = false)
	private Long sizeBytes;

	@Column(nullable = false)
	private Integer width;

	@Column(nullable = false)
	private Integer height;

	@Column(name = "uploaded_by")
	private String uploadedBy;

	@Column(name = "pending_since")
	private OffsetDateTime pendingSince;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	public UUID getOwnerId() {
		return switch (target) {
			case room_type -> roomTypeId;
			case product -> productId;
			case amenity -> amenityId;
			case inventory_item -> inventoryItemId;
		};
	}

	/**
	 * Asocia la imagen a un registro de su propio destino, o la deja pendiente
	 * con {@code null}. Mantiene la regla de la tabla: pendiente si y solo si
	 * no tiene dueño.
	 */
	public void assignOwner(UUID ownerId, OffsetDateTime now) {
		roomTypeId = target == MediaTarget.room_type ? ownerId : null;
		productId = target == MediaTarget.product ? ownerId : null;
		amenityId = target == MediaTarget.amenity ? ownerId : null;
		inventoryItemId = target == MediaTarget.inventory_item ? ownerId : null;
		if (ownerId == null) {
			position = null;
			primary = false;
			pendingSince = now;
		} else {
			pendingSince = null;
		}
		updatedAt = now;
	}

	public boolean isPending() {
		return getOwnerId() == null;
	}
}
