package com.aurora.pms.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRoomServiceOrderNotesRequest(
		/** Texto vacio limpia las observaciones. */
		@NotNull(message = "Notes are required")
		@Size(max = 1000, message = "Notes must be at most 1000 characters")
		String notes
) {
}
