package com.aurora.pms.dto.request;

import com.aurora.pms.model.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateRoomServiceOrderStatusRequest(
		@NotNull(message = "Status is required")
		OrderStatus status
) {
}
