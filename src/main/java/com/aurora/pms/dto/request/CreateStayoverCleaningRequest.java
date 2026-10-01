package com.aurora.pms.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStayoverCleaningRequest(
		@NotNull
		UUID bookingId,

		@Size(max = 1000)
		String description
) {
}
