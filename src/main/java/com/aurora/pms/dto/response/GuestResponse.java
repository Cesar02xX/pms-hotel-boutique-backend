package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.DocumentType;

public record GuestResponse(
		UUID id,
		String firstName,
		String lastName,
		String email,
		String phone,
		String nationality,
		DocumentType documentType,
		String documentNumber,
		String notes,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
