package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.aurora.pms.model.enums.OrderStatus;

public record RoomServiceOrderResponse(
		UUID id,
		UUID bookingId,
		UUID roomId,
		UUID guestId,
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
