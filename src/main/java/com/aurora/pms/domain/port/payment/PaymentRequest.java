package com.aurora.pms.domain.port.payment;

import java.util.Map;
import java.util.UUID;

/** Provider-neutral request to authorize or capture a payment. */
public record PaymentRequest(
		UUID paymentReference,
		long amountCents,
		String currency,
		String paymentMethod,
		String returnUrl,
		Map<String, String> metadata) {

	public PaymentRequest {
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}
}
