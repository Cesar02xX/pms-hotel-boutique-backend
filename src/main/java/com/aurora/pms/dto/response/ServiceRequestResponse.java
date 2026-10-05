package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;

public record ServiceRequestResponse(
		UUID id,
		UUID bookingId,
		UUID roomId,
		String roomNumber,
		UUID guestId,
		String guestName,
		UUID responsibleUserId,
		String responsibleUserName,
		String responsibleUserEmail,
		ServiceRequestType type,
		String description,
		ServiceRequestStatus status,
		String notes,
		OffsetDateTime requestedAt,
		OffsetDateTime startedAt,
		OffsetDateTime completedAt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
