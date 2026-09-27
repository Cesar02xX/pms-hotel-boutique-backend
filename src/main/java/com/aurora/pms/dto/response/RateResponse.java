package com.aurora.pms.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RateResponse(
		UUID id,
		UUID roomTypeId,
		String name,
		LocalDate validFrom,
		LocalDate validTo,
		Long priceCents,
		String currency,
		Integer minimumNights,
		Boolean refundable,
		Boolean active,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
