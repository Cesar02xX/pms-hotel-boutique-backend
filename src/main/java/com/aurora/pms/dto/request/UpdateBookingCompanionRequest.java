package com.aurora.pms.dto.request;

import com.aurora.pms.model.enums.DocumentType;
import com.aurora.pms.model.enums.GuestType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateBookingCompanionRequest(
		@NotBlank(message = "First name is required")
		String firstName,

		@NotBlank(message = "Last name is required")
		String lastName,

		DocumentType documentType,

		String documentNumber,

		@NotNull(message = "Guest type is required")
		GuestType guestType
) {
}
