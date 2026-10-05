package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.aurora.pms.model.enums.HousekeepingChecklistStatus;

public record HousekeepingChecklistResponse(
		UUID id,
		UUID serviceRequestId,
		UUID roomId,
		String roomNumber,
		HousekeepingChecklistStatus status,
		String observations,
		String responsibleUserEmail,
		String completedByUserEmail,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt,
		List<HousekeepingChecklistItemResponse> items
) {
}
