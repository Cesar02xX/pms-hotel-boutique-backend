package com.aurora.pms.dto.request;

import java.util.UUID;

import com.aurora.pms.model.enums.ServiceRequestType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateServiceRequestRequest(
		UUID bookingId,

		@NotNull(message = "Room id is required")
		UUID roomId,

		@NotNull(message = "Type is required")
		ServiceRequestType type,

		@NotBlank(message = "Description is required")
		String description,

		String notes
) {
}
