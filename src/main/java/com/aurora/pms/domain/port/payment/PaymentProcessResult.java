package com.aurora.pms.domain.port.payment;

/** Provider-neutral result of a payment attempt. */
public record PaymentProcessResult(
		String providerTransactionId,
		PaymentStatus status,
		String message,
		String checkoutUrl) {
}
