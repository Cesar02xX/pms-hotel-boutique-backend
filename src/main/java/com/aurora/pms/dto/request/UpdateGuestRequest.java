package com.aurora.pms.dto.request;

import com.aurora.pms.model.enums.DocumentType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateGuestRequest(
		@NotBlank(message = "First name is required")
		String firstName,

		@NotBlank(message = "Last name is required")
		String lastName,

		@Email(message = "Email must be valid")
		String email,

		String phone,

		String nationality,

		DocumentType documentType,

		String documentNumber,

		String notes
) {
}
