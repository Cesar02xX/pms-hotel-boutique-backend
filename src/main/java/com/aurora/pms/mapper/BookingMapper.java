package com.aurora.pms.mapper;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.CreateBookingRequest;
import com.aurora.pms.dto.request.UpdateBookingRequest;
import com.aurora.pms.dto.response.BookingResponse;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.BookingStatus;

@Component
public class BookingMapper {

	public BookingResponse toResponse(Booking booking) {
		return new BookingResponse(
				booking.getId(),
				booking.getConfirmationCode(),
				booking.getGuestLinkCode(),
				booking.getGuest().getId(),
				booking.getRoom() != null ? booking.getRoom().getId() : null,
				booking.getRoomType().getId(),
				booking.getRate() != null ? booking.getRate().getId() : null,
				booking.getCheckIn(),
				booking.getCheckOut(),
				booking.getStatus(),
				booking.getAdults(),
				booking.getChildren(),
				booking.getTotalAmountCents(),
				booking.getCurrency(),
				booking.getNotes(),
				booking.getCancellationReason(),
				booking.getCancelledAt(),
				booking.getCreatedAt(),
				booking.getUpdatedAt()
		);
	}

	public Booking toEntity(
			CreateBookingRequest request,
			Guest guest,
			RoomType roomType,
			Room room,
			Rate rate
	) {
		Booking booking = new Booking();
		booking.setGuest(guest);
		booking.setRoomType(roomType);
		booking.setRoom(room);
		booking.setRate(rate);
		booking.setCheckIn(request.checkIn());
		booking.setCheckOut(request.checkOut());
		booking.setStatus(BookingStatus.pending);
		booking.setAdults(request.adults());
		booking.setChildren(request.children());
		booking.setCurrency("GTQ");
		booking.setNotes(request.notes());
		return booking;
	}

	public void applyUpdate(Booking booking, UpdateBookingRequest request) {
		if (request.checkIn() != null) {
			booking.setCheckIn(request.checkIn());
		}
		if (request.checkOut() != null) {
			booking.setCheckOut(request.checkOut());
		}
		if (request.adults() != null) {
			booking.setAdults(request.adults());
		}
		if (request.children() != null) {
			booking.setChildren(request.children());
		}
		if (request.status() != null) {
			booking.setStatus(request.status());
		}
		if (request.notes() != null) {
			booking.setNotes(request.notes());
		}
	}
}
