package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record HousekeepingChecklistTemplateResponse(
		String code,
		String name,
		List<String> items,
		OffsetDateTime updatedAt
) {
}
