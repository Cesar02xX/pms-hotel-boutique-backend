package com.aurora.pms.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PromotionResponse(
		UUID id,
		String code,
		String name,
		String description,
		Integer discountPercent,
		LocalDate validFrom,
		LocalDate validTo,
		Boolean active,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
