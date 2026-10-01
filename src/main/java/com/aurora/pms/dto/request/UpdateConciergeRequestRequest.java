package com.aurora.pms.dto.request;

import jakarta.validation.constraints.AssertTrue;

public record UpdateConciergeRequestRequest(
		String description,
		String notes
) {

	@AssertTrue(message = "At least one field must be provided")
	public boolean hasChanges() {
		return description != null || notes != null;
	}

	@AssertTrue(message = "Description must not be blank")
	public boolean hasValidDescription() {
		return description == null || !description.isBlank();
	}
}
