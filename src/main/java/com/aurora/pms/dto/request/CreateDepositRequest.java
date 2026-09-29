package com.aurora.pms.dto.request;

import com.aurora.pms.model.enums.DepositMethod;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateDepositRequest(
		@NotNull(message = "Amount is required")
		@Positive(message = "Amount must be greater than 0")
		@JsonDeserialize(using = WholeCentsDeserializer.class)
		Long amountCents,

		@NotNull(message = "Deposit method is required")
		DepositMethod method,

		String notes
) {
}
