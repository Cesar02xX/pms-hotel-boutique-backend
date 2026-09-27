package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoomFeatureResponse(
		UUID id,
		String name,
		String description,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
