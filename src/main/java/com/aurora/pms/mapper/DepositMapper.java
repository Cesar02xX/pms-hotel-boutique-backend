package com.aurora.pms.mapper;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.CreateDepositRequest;
import com.aurora.pms.dto.response.DepositResponse;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Deposit;

@Component
public class DepositMapper {

	public DepositResponse toResponse(Deposit deposit) {
		return new DepositResponse(
				deposit.getId(),
				deposit.getBooking().getId(),
				deposit.getGuest().getId(),
				deposit.getAmountCents(),
				deposit.getCurrency(),
				deposit.getMethod(),
				deposit.getStatus(),
				deposit.getCollectedAt(),
				deposit.getRefundedAt(),
				deposit.getNotes(),
				deposit.getCreatedAt(),
				deposit.getUpdatedAt()
		);
	}

	public Deposit toEntity(CreateDepositRequest request, Booking booking) {
		Deposit deposit = new Deposit();
		deposit.setBooking(booking);
		deposit.setGuest(booking.getGuest());
		deposit.setAmountCents(request.amountCents());
		deposit.setMethod(request.method());
		deposit.setNotes(trimToNull(request.notes()));
		return deposit;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
