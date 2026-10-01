package com.aurora.pms.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateGuestRoomServiceOrderRequest(
		String notes,

		@NotEmpty(message = "At least one item is required")
		List<@NotNull(message = "Item is required") @Valid CreateRoomServiceOrderItemRequest> items
) {
}
