package com.aurora.pms.dto.response;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.AmenityCategory;

public record AmenityResponse(
		UUID id,
		String name,
		String description,
		AmenityCategory category,
		String location,
		LocalTime opensAt,
		LocalTime closesAt,
		Boolean active,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
