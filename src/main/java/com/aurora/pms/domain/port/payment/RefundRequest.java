package com.aurora.pms.domain.port.payment;

import java.util.Map;

/** Provider-neutral request to refund a payment. */
public record RefundRequest(
		String providerTransactionId,
		long amountCents,
		String currency,
		String reason,
		Map<String, String> metadata) {

	public RefundRequest {
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}
}
