package com.aurora.pms.dto.response;

import java.util.UUID;

/** Imagen asociada a un tipo de habitación, producto o amenidad, en el orden de su galería. */
public record MediaImageResponse(
		UUID id,
		String altText,
		Integer position,
		Boolean primary,
		Integer width,
		Integer height,
		MediaImageUrls urls
) {
}
