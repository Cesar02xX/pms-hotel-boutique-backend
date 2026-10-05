package com.aurora.pms.dto.response;

import java.util.List;
import java.util.UUID;

public record PublicRoomTypeResponse(
		UUID id,
		String code,
		String name,
		String description,
		Integer capacity,
		String bedConfiguration,
		List<PublicRoomFeatureResponse> features
) {
}
