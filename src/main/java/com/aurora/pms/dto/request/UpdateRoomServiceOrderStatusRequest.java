package com.aurora.pms.dto.request;

import com.aurora.pms.model.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRoomServiceOrderStatusRequest(
		@NotNull(message = "Status is required")
		OrderStatus status,

		/** Opcional: motivo de rechazo/cancelacion u observacion. Null conserva las notas actuales. */
		@Size(max = 1000, message = "Notes must be at most 1000 characters")
		String notes
) {
}
