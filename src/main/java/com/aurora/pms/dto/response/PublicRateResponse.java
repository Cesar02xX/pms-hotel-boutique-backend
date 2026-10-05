package com.aurora.pms.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record PublicRateResponse(
		UUID id,
		UUID roomTypeId,
		String name,
		LocalDate validFrom,
		LocalDate validTo,
		Long priceCents,
		String currency,
		Integer minimumNights,
		Boolean refundable
) {
}
