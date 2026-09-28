package com.aurora.pms.mapper;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.response.CheckInResponse;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Room;

@Component
public class CheckInMapper {

	public CheckInResponse toResponse(Booking booking, Room room, int companionCount, OffsetDateTime operationTimestamp) {
		return new CheckInResponse(
				booking.getId(),
				booking.getStatus(),
				booking.getGuest().getId(),
				booking.getGuest().getFirstName(),
				booking.getGuest().getLastName(),
				room.getId(),
				room.getRoomNumber(),
				booking.getCheckIn(),
				booking.getCheckOut(),
				booking.getAdults(),
				booking.getChildren(),
				companionCount,
				booking.getAdults() + booking.getChildren(),
				operationTimestamp
		);
	}
}
