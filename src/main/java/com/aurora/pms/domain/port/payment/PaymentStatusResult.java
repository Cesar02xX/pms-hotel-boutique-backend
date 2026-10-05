package com.aurora.pms.domain.port.payment;

/** Result of validating and interpreting a payment-provider webhook. */
public record PaymentStatusResult(
		boolean signatureValid,
		String providerTransactionId,
		PaymentStatus status,
		String message) {
}
