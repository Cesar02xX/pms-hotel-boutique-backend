package com.aurora.pms.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.annotation.JsonDeserialize;

public record OpenCashSessionRequest(
		@NotNull(message = "Opening balance is required")
		@PositiveOrZero(message = "Opening balance must be 0 or greater")
		@JsonDeserialize(using = WholeCentsDeserializer.class)
		Long openingBalanceCents,

		String notes
) {
}
