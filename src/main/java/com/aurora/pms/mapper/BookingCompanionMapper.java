package com.aurora.pms.mapper;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.CreateBookingCompanionRequest;
import com.aurora.pms.dto.request.UpdateBookingCompanionRequest;
import com.aurora.pms.dto.response.BookingCompanionResponse;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.BookingCompanion;

@Component
public class BookingCompanionMapper {

	public BookingCompanionResponse toResponse(BookingCompanion companion) {
		return new BookingCompanionResponse(
				companion.getId(),
				companion.getBooking().getId(),
				companion.getFirstName(),
				companion.getLastName(),
				companion.getDocumentType(),
				companion.getDocumentNumber(),
				companion.getGuestType(),
				companion.getCreatedAt(),
				companion.getUpdatedAt()
		);
	}

	public BookingCompanion toEntity(CreateBookingCompanionRequest request, Booking booking) {
		BookingCompanion companion = new BookingCompanion();
		companion.setBooking(booking);
		companion.setFirstName(request.firstName().trim());
		companion.setLastName(request.lastName().trim());
		companion.setDocumentType(request.documentType());
		companion.setDocumentNumber(trimToNull(request.documentNumber()));
		companion.setGuestType(request.guestType());
		return companion;
	}

	public void applyUpdate(BookingCompanion companion, UpdateBookingCompanionRequest request) {
		companion.setFirstName(request.firstName().trim());
		companion.setLastName(request.lastName().trim());
		companion.setDocumentType(request.documentType());
		companion.setDocumentNumber(trimToNull(request.documentNumber()));
		companion.setGuestType(request.guestType());
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
