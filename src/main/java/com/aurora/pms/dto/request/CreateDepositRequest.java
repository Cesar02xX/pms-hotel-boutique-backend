package com.aurora.pms.dto.request;

import com.aurora.pms.model.enums.DepositMethod;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateDepositRequest(
		@NotNull(message = "Amount is required")
		@Positive(message = "Amount must be greater than 0")
		Long amountCents,

		@NotNull(message = "Deposit method is required")
		DepositMethod method,

		String notes
) {
}
