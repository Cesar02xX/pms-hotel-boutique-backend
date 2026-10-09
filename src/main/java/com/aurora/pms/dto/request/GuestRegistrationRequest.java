package com.aurora.pms.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestRegistrationRequest(
		@NotBlank(message = "Reservation code is required") String code,
		@NotBlank(message = "Email is required") @Email(message = "Email must be valid") String email,
		@NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must contain between 8 and 72 characters") String password
) {
}
