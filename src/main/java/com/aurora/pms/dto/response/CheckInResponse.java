package com.aurora.pms.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.BookingStatus;

public record CheckInResponse(
		UUID bookingId,
		BookingStatus status,
		UUID guestId,
		String guestFirstName,
		String guestLastName,
		UUID roomId,
		String roomNumber,
		LocalDate checkIn,
		LocalDate checkOut,
		Integer adults,
		Integer children,
		Integer companionCount,
		Integer totalOccupants,
		OffsetDateTime operationTimestamp
) {
}
