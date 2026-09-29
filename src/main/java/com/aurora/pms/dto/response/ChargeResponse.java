package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.ChargeCategory;
import com.aurora.pms.model.enums.ChargeStatus;

public record ChargeResponse(
		UUID id,
		UUID bookingId,
		UUID productId,
		String description,
		Integer quantity,
		Long unitPriceCents,
		Long amountCents,
		String currency,
		ChargeCategory category,
		ChargeStatus status,
		OffsetDateTime chargedAt,
		UUID createdByUserId,
		String voidReason,
		OffsetDateTime createdAt
) {
}
