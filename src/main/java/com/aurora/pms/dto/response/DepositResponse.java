package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.DepositMethod;
import com.aurora.pms.model.enums.DepositStatus;

public record DepositResponse(
		UUID id,
		UUID bookingId,
		UUID guestId,
		Long amountCents,
		String currency,
		DepositMethod method,
		DepositStatus status,
		OffsetDateTime collectedAt,
		OffsetDateTime refundedAt,
		String notes,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
