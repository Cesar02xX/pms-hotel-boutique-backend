package com.aurora.pms.dto.response;

import java.util.List;
import java.util.UUID;

public record StayReceiptResponse(
		GuestStayResponse stay,
		List<ChargeResponse> charges,
		List<PaymentResponse> payments,
		List<DepositResponse> deposits,
		Long finalBalanceCents,
		String currency
) {
}
