package com.aurora.pms.dto.request;

import com.aurora.pms.model.enums.ServiceRequestStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateConciergeRequestStatusRequest(
		@NotNull(message = "Status is required")
		ServiceRequestStatus status,

		String notes
) {
}
