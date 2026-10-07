package com.aurora.pms.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record UpdateRoomTypeRequest(
		@Pattern(regexp = ".*\\S.*", message = "Code must not be blank")
		String code,

		@Pattern(regexp = ".*\\S.*", message = "Name must not be blank")
		String name,

		String description,

		@Positive(message = "Capacity must be greater than zero")
		Integer capacity,

		String bedConfiguration,

		List<@NotNull(message = "Room feature id must not be null") UUID> roomFeatureIds,

		Boolean active,

		// Galería ordenada; null no cambia nada y una lista vacía quita todas (ver MediaImageAssignmentRequest).
		List<@Valid MediaImageAssignmentRequest> images
) {
}
