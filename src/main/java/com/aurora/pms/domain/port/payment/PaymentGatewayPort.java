package com.aurora.pms.domain.port.payment;

/** Port for a provider of local payment services. */
public interface PaymentGatewayPort {

	PaymentProcessResult processPayment(PaymentRequest request);

	PaymentRefundResult processRefund(RefundRequest request);

	PaymentStatusResult verifyWebhookSignature(String payload, String signature);
}
