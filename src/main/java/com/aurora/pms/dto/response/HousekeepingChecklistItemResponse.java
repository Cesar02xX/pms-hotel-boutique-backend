package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HousekeepingChecklistItemResponse(
		UUID id,
		String label,
		boolean checked,
		int position,
		String notes,
		String checkedByUserEmail,
		OffsetDateTime checkedAt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
