package com.aurora.pms.dto.response;

import java.util.UUID;

public record PublicRoomFeatureResponse(
		UUID id,
		String name,
		String description
) {
}
