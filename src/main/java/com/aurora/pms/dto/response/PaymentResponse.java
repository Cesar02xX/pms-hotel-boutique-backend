package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.PaymentMethod;
import com.aurora.pms.model.enums.PaymentStatus;

public record PaymentResponse(
		UUID id,
		UUID bookingId,
		Long amountCents,
		String currency,
		PaymentMethod method,
		PaymentStatus status,
		String transactionReference,
		OffsetDateTime paidAt,
		UUID processedByUserId,
		OffsetDateTime createdAt
) {
}
