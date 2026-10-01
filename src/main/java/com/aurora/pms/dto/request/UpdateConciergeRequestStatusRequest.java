package com.aurora.pms.dto.request;

import java.util.UUID;

import com.aurora.pms.model.enums.ServiceRequestStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateConciergeRequestStatusRequest(
		@NotNull(message = "Status is required")
		ServiceRequestStatus status,

		UUID responsibleUserId,

		String notes
) {
}
