package com.aurora.pms.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateRoomServiceOrderRequest(
		@NotNull(message = "Booking id is required")
		UUID bookingId,

		String notes,

		@NotEmpty(message = "At least one item is required")
		List<@NotNull(message = "Item is required") @Valid CreateRoomServiceOrderItemRequest> items
) {
}
