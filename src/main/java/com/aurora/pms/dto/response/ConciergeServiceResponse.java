package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConciergeServiceResponse(
		UUID id,
		String name,
		String description,
		boolean active,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
