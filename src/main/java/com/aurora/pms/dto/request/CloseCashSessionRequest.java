package com.aurora.pms.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CloseCashSessionRequest(
		@NotNull(message = "Counted balance is required")
		@PositiveOrZero(message = "Counted balance must be 0 or greater")
		@JsonDeserialize(using = WholeCentsDeserializer.class)
		Long countedBalanceCents,

		String notes
) {
}
