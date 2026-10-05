package com.aurora.pms.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.BookingStatus;

/**
 * Confirmación de una reserva pública. No incluye el id interno de la reserva,
 * el del huésped, la habitación ni el guestLinkCode (credencial del portal).
 */
public record PublicBookingResponse(
		String confirmationCode,
		BookingStatus status,
		UUID roomTypeId,
		String roomTypeName,
		LocalDate checkIn,
		LocalDate checkOut,
		Integer nights,
		Integer adults,
		Integer children,
		String rateName,
		Long totalAmountCents,
		String currency,
		String guestFirstName,
		String guestLastName,
		String guestEmail,
		OffsetDateTime createdAt
) {
}
