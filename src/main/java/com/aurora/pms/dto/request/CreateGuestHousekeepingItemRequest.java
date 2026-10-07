package com.aurora.pms.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateGuestHousekeepingItemRequest(
		@NotNull UUID itemId,
		@NotNull @Min(1) @Max(5) Integer quantity,
		String notes
) {
}
