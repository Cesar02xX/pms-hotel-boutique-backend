package com.aurora.pms.mapper;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.CreateConciergeRequestRequest;
import com.aurora.pms.dto.response.ConciergeRequestResponse;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.ServiceRequest;

@Component
public class ConciergeRequestMapper {

	public ConciergeRequestResponse toResponse(ServiceRequest request) {
		return new ConciergeRequestResponse(
				request.getId(),
				request.getBooking().getId(),
				request.getRoom() != null ? request.getRoom().getId() : null,
				request.getGuest() != null ? request.getGuest().getId() : null,
				request.getResponsibleUser() != null ? request.getResponsibleUser().getId() : null,
				request.getType(),
				request.getDescription(),
				request.getStatus(),
				request.getNotes(),
				request.getCharge() != null ? request.getCharge().getId() : null,
				request.getRequestedAt(),
				request.getCreatedAt(),
				request.getUpdatedAt()
		);
	}

	/** Room y guest salen de la reserva, nunca del cliente. */
	public ServiceRequest toEntity(CreateConciergeRequestRequest request, Booking booking) {
		ServiceRequest serviceRequest = new ServiceRequest();
		serviceRequest.setBooking(booking);
		serviceRequest.setRoom(booking.getRoom());
		serviceRequest.setGuest(booking.getGuest());
		serviceRequest.setDescription(request.description().trim());
		serviceRequest.setNotes(trimToNull(request.notes()));
		return serviceRequest;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
