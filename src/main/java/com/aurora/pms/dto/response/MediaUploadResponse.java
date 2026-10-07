package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.MediaTarget;

/**
 * Imagen recién subida y todavía pendiente. Sus URLs públicas responden 404
 * hasta que se asocia a un registro activo; mientras tanto la vista previa se
 * hace con el archivo local o con GET /api/v1/media/{id}/content/{variant}.
 */
public record MediaUploadResponse(
		UUID id,
		MediaTarget target,
		String contentType,
		Long sizeBytes,
		Integer width,
		Integer height,
		MediaImageUrls urls,
		OffsetDateTime expiresAt
) {
}
