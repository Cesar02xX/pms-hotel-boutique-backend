package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.aurora.pms.model.enums.GuestAccountStatus;

public record GuestFolioResponse(
		UUID accountId,
		UUID bookingId,
		UUID guestId,
		GuestAccountStatus status,
		Long balanceCents,
		String currency,
		OffsetDateTime openedAt,
		OffsetDateTime closedAt,
		Long activeChargesCents,
		Long voidedChargesCents,
		Long completedPaymentsCents,
		List<ChargeResponse> charges
) {
}
