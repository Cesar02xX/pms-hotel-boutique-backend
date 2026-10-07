package com.aurora.pms.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.aurora.pms.domain.port.storage.StoredObject;
import com.aurora.pms.dto.request.MediaImageAssignmentRequest;
import com.aurora.pms.dto.response.MediaImageResponse;
import com.aurora.pms.dto.response.MediaUploadResponse;
import com.aurora.pms.model.enums.MediaTarget;
import com.aurora.pms.model.enums.MediaVariant;

public interface MediaImageService {

	/** Valida, genera variantes y guarda la imagen como pendiente. */
	MediaUploadResponse upload(MediaTarget target, byte[] content);

	/** Borra una imagen pendiente. Una asociada se quita desde su registro, no por aquí. */
	void deletePending(UUID id);

	/** Variante de una imagen pública (asociada a un registro activo). 404 en cualquier otro caso. */
	StoredObject readPublic(UUID id, MediaVariant variant);

	/** Variante de cualquier imagen, para el personal con permiso de lectura de su catálogo. */
	StoredObject readForStaff(UUID id, MediaVariant variant);

	/**
	 * Reemplaza la galería de un registro por la lista recibida. {@code null}
	 * no cambia nada; una lista vacía quita todas. Las imágenes que salen de
	 * la galería quedan pendientes y la limpieza las borra pasado el plazo.
	 */
	void replaceImages(MediaTarget target, UUID ownerId, List<MediaImageAssignmentRequest> images);

	List<MediaImageResponse> findImages(MediaTarget target, UUID ownerId);

	/** Una sola consulta para varios registros; los que no tienen imágenes no aparecen en el mapa. */
	Map<UUID, List<MediaImageResponse>> findImages(MediaTarget target, Collection<UUID> ownerIds);

	/** Borra las imágenes pendientes vencidas. Devuelve cuántas se borraron. */
	int deleteExpiredPending();
}
