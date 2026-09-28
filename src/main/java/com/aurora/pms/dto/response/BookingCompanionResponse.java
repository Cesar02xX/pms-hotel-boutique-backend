package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.DocumentType;
import com.aurora.pms.model.enums.GuestType;

public record BookingCompanionResponse(
		UUID id,
		UUID bookingId,
		String firstName,
		String lastName,
		DocumentType documentType,
		String documentNumber,
		GuestType guestType,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
