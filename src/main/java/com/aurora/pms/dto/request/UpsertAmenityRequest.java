package com.aurora.pms.dto.request;

import java.time.LocalTime;

import com.aurora.pms.model.enums.AmenityCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpsertAmenityRequest(
		@NotBlank String name,
		String description,
		@NotNull AmenityCategory category,
		String location,
		LocalTime opensAt,
		LocalTime closesAt,
		Boolean active
) {
}
