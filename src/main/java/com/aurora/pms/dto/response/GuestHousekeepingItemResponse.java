package com.aurora.pms.dto.response;

import java.util.List;
import java.util.UUID;

public record GuestHousekeepingItemResponse(
		UUID id,
		String name,
		String description,
		String unit,
		Integer currentQuantity,
		List<MediaImageResponse> images
) {
}
