package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;

public record ConciergeRequestResponse(
		UUID id,
		UUID bookingId,
		UUID roomId,
		/** Para que el personal vea la habitacion sin necesitar rooms.read. */
		String roomNumber,
		UUID guestId,
		String guestName,
		UUID responsibleUserId,
		/** Para mostrar el responsable sin necesitar acceso a /admin/users. */
		String responsibleUserName,
		String responsibleUserEmail,
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
