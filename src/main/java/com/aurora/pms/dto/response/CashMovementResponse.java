package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.CashMovementType;

public record CashMovementResponse(
		UUID id,
		UUID cashSessionId,
		CashMovementType type,
		String concept,
		Long amountCents,
		String currency,
		UUID responsibleUserId,
		OffsetDateTime occurredAt,
		UUID paymentId,
		OffsetDateTime createdAt
) {
}
