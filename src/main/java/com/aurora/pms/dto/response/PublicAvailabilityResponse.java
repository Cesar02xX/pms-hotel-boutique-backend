package com.aurora.pms.dto.response;

import java.time.LocalDate;
import java.util.List;

public record PublicAvailabilityResponse(
		LocalDate checkIn,
		LocalDate checkOut,
		Integer nights,
		Integer adults,
		Integer children,
		List<PublicAvailabilityResult> results
) {
}
