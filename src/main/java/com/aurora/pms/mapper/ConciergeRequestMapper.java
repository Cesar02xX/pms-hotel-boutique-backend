package com.aurora.pms.mapper;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.CreateConciergeRequestRequest;
import com.aurora.pms.dto.response.ConciergeRequestResponse;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.User;

@Component
public class ConciergeRequestMapper {

	public ConciergeRequestResponse toResponse(ServiceRequest request) {
		User responsible = request.getResponsibleUser();
		User completedBy = request.getCompletedByUser();
		return new ConciergeRequestResponse(
				request.getId(),
				request.getBooking().getId(),
				request.getRoom() != null ? request.getRoom().getId() : null,
				request.getRoom() != null ? request.getRoom().getRoomNumber() : null,
				request.getGuest() != null ? request.getGuest().getId() : null,
				request.getGuest() != null
						? request.getGuest().getFirstName() + " " + request.getGuest().getLastName()
						: null,
				responsible != null ? responsible.getId() : null,
				responsible != null ? responsible.getFirstName() + " " + responsible.getLastName() : null,
				responsible != null ? responsible.getEmail() : null,
				request.getType(),
				request.getDescription(),
				request.getStatus(),
				request.getNotes(),
				request.getCharge() != null ? request.getCharge().getId() : null,
				request.getRequestedAt(),
				request.getCreatedAt(),
				request.getUpdatedAt(),
				completedBy != null ? completedBy.getEmail() : null
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
