package com.aurora.pms.dto.request;

import com.aurora.pms.model.enums.PaymentMethod;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreatePaymentRequest(
		@NotNull(message = "Amount is required")
		@Positive(message = "Amount must be greater than 0")
		@JsonDeserialize(using = WholeCentsDeserializer.class)
		Long amountCents,

		@NotNull(message = "Payment method is required")
		PaymentMethod method,

		String transactionReference
) {
}
