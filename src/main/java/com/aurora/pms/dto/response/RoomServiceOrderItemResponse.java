package com.aurora.pms.dto.response;

import java.util.UUID;

public record RoomServiceOrderItemResponse(
		UUID id,
		UUID productId,
		String productName,
		Integer quantity,
		Long unitPriceCents,
		Long lineTotalCents
) {
}
