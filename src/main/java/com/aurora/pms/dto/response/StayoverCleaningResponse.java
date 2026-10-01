package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.ServiceRequestStatus;

public record StayoverCleaningResponse(
		UUID id,
		UUID bookingId,
		UUID roomId,
		String roomNumber,
		ServiceRequestStatus status,
		String description,
		String notes,
		String responsibleUserEmail,
		String startedByUserEmail,
		String completedByUserEmail,
		OffsetDateTime requestedAt,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
