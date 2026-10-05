package com.aurora.pms.mapper;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.CreateServiceRequestRequest;
import com.aurora.pms.dto.response.ServiceRequestResponse;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.User;

@Component
public class ServiceRequestMapper {

	public ServiceRequestResponse toResponse(ServiceRequest request) {
		User responsible = request.getResponsibleUser();
		return new ServiceRequestResponse(
				request.getId(),
				request.getBooking() != null ? request.getBooking().getId() : null,
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
				request.getRequestedAt(),
				request.getStartedAt(),
				request.getCompletedAt(),
				request.getCreatedAt(),
				request.getUpdatedAt()
		);
	}

	public ServiceRequest toEntity(CreateServiceRequestRequest request, Booking booking, Room room) {
		ServiceRequest serviceRequest = new ServiceRequest();
		serviceRequest.setBooking(booking);
		serviceRequest.setRoom(room);
		serviceRequest.setGuest(booking != null ? booking.getGuest() : null);
		serviceRequest.setType(request.type());
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
