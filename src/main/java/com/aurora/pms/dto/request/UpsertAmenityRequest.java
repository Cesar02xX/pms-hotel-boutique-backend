package com.aurora.pms.dto.request;

import java.time.LocalTime;
import java.util.List;

import com.aurora.pms.model.enums.AmenityCategory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpsertAmenityRequest(
		@NotBlank String name,
		String description,
		@NotNull AmenityCategory category,
		String location,
		LocalTime opensAt,
		LocalTime closesAt,
		Boolean active,
		// Galería ordenada; null no cambia nada y una lista vacía quita todas (ver MediaImageAssignmentRequest).
		List<@Valid MediaImageAssignmentRequest> images
) {
}
