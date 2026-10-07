package com.aurora.pms.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Una imagen ya subida con POST /api/v1/media que se asocia a un registro.
 * El orden de la lista que la contiene es el orden de la galería. Si ninguna
 * llega con {@code primary = true}, la primera de la lista es la principal.
 */
public record MediaImageAssignmentRequest(
		@NotNull(message = "Media id is required")
		UUID mediaId,

		@Size(max = 255, message = "Alt text must have at most 255 characters")
		String altText,

		Boolean primary
) {
}
