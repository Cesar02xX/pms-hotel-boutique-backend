package com.aurora.pms.dto.request;

import com.aurora.pms.model.enums.DocumentType;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicGuestRequest(
		@NotBlank(message = "First name is required")
		@Size(max = 100, message = "First name must be 100 characters or fewer")
		String firstName,

		@NotBlank(message = "Last name is required")
		@Size(max = 100, message = "Last name must be 100 characters or fewer")
		String lastName,

		@NotBlank(message = "Email is required")
		@Email(message = "Email must be valid")
		@Size(max = 254, message = "Email must be 254 characters or fewer")
		String email,

		@Size(max = 40, message = "Phone must be 40 characters or fewer")
		String phone,

		@Size(max = 80, message = "Nationality must be 80 characters or fewer")
		String nationality,

		DocumentType documentType,

		@Size(max = 60, message = "Document number must be 60 characters or fewer")
		String documentNumber
) {
}
