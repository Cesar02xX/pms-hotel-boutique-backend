package com.aurora.pms.mapper;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.CreatePaymentRequest;
import com.aurora.pms.dto.response.PaymentResponse;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Payment;

@Component
public class PaymentMapper {

	public PaymentResponse toResponse(Payment payment) {
		return new PaymentResponse(
				payment.getId(),
				payment.getBooking().getId(),
				payment.getAmountCents(),
				payment.getCurrency(),
				payment.getMethod(),
				payment.getStatus(),
				payment.getTransactionReference(),
				payment.getPaidAt(),
				payment.getProcessedByUser() != null ? payment.getProcessedByUser().getId() : null,
				payment.getCreatedAt()
		);
	}

	public Payment toEntity(CreatePaymentRequest request, Booking booking) {
		Payment payment = new Payment();
		payment.setBooking(booking);
		payment.setAmountCents(request.amountCents());
		payment.setMethod(request.method());
		payment.setTransactionReference(trimToNull(request.transactionReference()));
		return payment;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
