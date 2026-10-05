package com.aurora.pms.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.BookingStatus;

public record BookingResponse(
		UUID id,
		String confirmationCode,
		String guestLinkCode,
		UUID guestId,
		UUID roomId,
		UUID roomTypeId,
		UUID rateId,
		LocalDate checkIn,
		LocalDate checkOut,
		BookingStatus status,
		Integer adults,
		Integer children,
		Long totalAmountCents,
		String currency,
		String notes,
		String cancellationReason,
		OffsetDateTime cancelledAt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
