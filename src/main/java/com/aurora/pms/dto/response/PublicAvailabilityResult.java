package com.aurora.pms.dto.response;

import java.util.UUID;

public record PublicAvailabilityResult(
		UUID roomTypeId,
		String code,
		String name,
		Integer capacity,
		String bedConfiguration,
		Integer availableRooms,
		PublicRateResponse rate,
		Long totalAmountCents,
		String currency
) {
}
