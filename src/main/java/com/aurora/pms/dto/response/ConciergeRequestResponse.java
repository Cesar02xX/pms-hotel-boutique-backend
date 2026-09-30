package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;

public record ConciergeRequestResponse(
		UUID id,
		UUID bookingId,
		UUID roomId,
		UUID guestId,
		ServiceRequestType type,
		String description,
		ServiceRequestStatus status,
		String notes,
		UUID chargeId,
		OffsetDateTime requestedAt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
