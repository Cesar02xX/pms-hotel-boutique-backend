package com.aurora.pms.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateHousekeepingChecklistItemRequest(
		UUID id,

		@NotBlank
		@Size(max = 255)
		String label,

		Boolean checked,

		@Size(max = 1000)
		String notes
) {
}
