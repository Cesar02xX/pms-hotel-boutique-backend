package com.aurora.pms.dto.request;

import com.aurora.pms.model.enums.CashMovementType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateCashMovementRequest(
		@NotNull(message = "Movement type is required")
		CashMovementType type,

		@NotBlank(message = "Concept is required")
		@Size(max = 255, message = "Concept must be at most 255 characters")
		String concept,

		@NotNull(message = "Amount is required")
		@Positive(message = "Amount must be greater than 0")
		@JsonDeserialize(using = WholeCentsDeserializer.class)
		Long amountCents
) {
}
