package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.aurora.pms.model.enums.OrderStatus;

public record RoomServiceOrderResponse(
		UUID id,
		UUID bookingId,
		UUID roomId,
		/** Para que el personal sepa a donde entregar sin necesitar rooms.read. */
		String roomNumber,
		UUID guestId,
		String guestName,
		OrderStatus status,
		String notes,
		String currency,
		Long totalCents,
		List<RoomServiceOrderItemResponse> items,
		UUID chargeId,
		OffsetDateTime requestedAt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
