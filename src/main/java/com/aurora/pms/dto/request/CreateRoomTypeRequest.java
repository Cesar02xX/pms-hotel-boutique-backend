package com.aurora.pms.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateRoomTypeRequest(
		@NotBlank(message = "Code is required")
		String code,

		@NotBlank(message = "Name is required")
		String name,

		String description,

		@NotNull(message = "Capacity is required")
		@Positive(message = "Capacity must be greater than zero")
		Integer capacity,

		String bedConfiguration,

		List<@NotNull(message = "Room feature id must not be null") UUID> roomFeatureIds,

		Boolean active
) {
}
