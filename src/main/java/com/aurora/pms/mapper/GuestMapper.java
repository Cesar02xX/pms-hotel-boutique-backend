package com.aurora.pms.mapper;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.CreateGuestRequest;
import com.aurora.pms.dto.request.UpdateGuestRequest;
import com.aurora.pms.dto.response.GuestResponse;
import com.aurora.pms.model.Guest;

@Component
public class GuestMapper {

	public GuestResponse toResponse(Guest guest) {
		return new GuestResponse(
				guest.getId(),
				guest.getFirstName(),
				guest.getLastName(),
				guest.getEmail(),
				guest.getPhone(),
				guest.getNationality(),
				guest.getDocumentType(),
				guest.getDocumentNumber(),
				guest.getNotes(),
				guest.getCreatedAt(),
				guest.getUpdatedAt()
		);
	}

	public Guest toEntity(CreateGuestRequest request) {
		Guest guest = new Guest();
		guest.setFirstName(request.firstName().trim());
		guest.setLastName(request.lastName().trim());
		guest.setEmail(trimToNull(request.email()));
		guest.setPhone(trimToNull(request.phone()));
		guest.setNationality(trimToNull(request.nationality()));
		guest.setDocumentType(request.documentType());
		guest.setDocumentNumber(trimToNull(request.documentNumber()));
		guest.setNotes(request.notes());
		return guest;
	}

	public void applyUpdate(Guest guest, UpdateGuestRequest request) {
		guest.setFirstName(request.firstName().trim());
		guest.setLastName(request.lastName().trim());
		guest.setEmail(trimToNull(request.email()));
		guest.setPhone(trimToNull(request.phone()));
		guest.setNationality(trimToNull(request.nationality()));
		guest.setDocumentType(request.documentType());
		guest.setDocumentNumber(trimToNull(request.documentNumber()));
		guest.setNotes(request.notes());
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
