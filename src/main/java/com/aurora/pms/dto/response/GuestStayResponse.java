package com.aurora.pms.dto.response;

import java.time.LocalDate;
import java.util.UUID;

import com.aurora.pms.model.enums.BookingStatus;

public record GuestStayResponse(
		UUID bookingId,
		UUID guestId,
		String guestFirstName,
		String guestLastName,
		UUID roomId,
		String roomNumber,
		UUID roomTypeId,
		String roomTypeName,
		LocalDate checkIn,
		LocalDate checkOut,
		BookingStatus status,
		Long balanceCents,
		String currency
) {
}
