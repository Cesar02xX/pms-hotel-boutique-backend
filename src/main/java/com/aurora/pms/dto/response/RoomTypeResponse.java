package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RoomTypeResponse(
		UUID id,
		String code,
		String name,
		String description,
		Integer capacity,
		String bedConfiguration,
		List<UUID> roomFeatureIds,
		Boolean active,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt,
		List<MediaImageResponse> images
) {
}
