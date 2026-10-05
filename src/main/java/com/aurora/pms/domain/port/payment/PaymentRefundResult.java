package com.aurora.pms.domain.port.payment;

/** Provider-neutral result of a refund attempt. */
public record PaymentRefundResult(
		String providerRefundId,
		PaymentStatus status,
		String message) {
}
