package com.aurora.pms.dto.response;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.aurora.pms.model.enums.AmenityCategory;

/** Amenidad activa para la web pública: sin banderas internas ni timestamps de auditoría. */
public record PublicAmenityResponse(
		UUID id,
		String name,
		String description,
		AmenityCategory category,
		String location,
		LocalTime opensAt,
		LocalTime closesAt,
		List<MediaImageResponse> images
) {
}
