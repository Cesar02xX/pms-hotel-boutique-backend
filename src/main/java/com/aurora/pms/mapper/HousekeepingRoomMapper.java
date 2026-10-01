package com.aurora.pms.mapper;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.response.HousekeepingRoomResponse;
import com.aurora.pms.model.Room;

@Component
public class HousekeepingRoomMapper {

	public HousekeepingRoomResponse toResponse(Room room) {
		return new HousekeepingRoomResponse(
				room.getId(),
				room.getRoomNumber(),
				room.getRoomType().getId(),
				room.getFloor(),
				room.getStatus(),
				room.getHousekeepingStatus(),
				room.getNotes(),
				room.getCleaningUser() != null ? room.getCleaningUser().getEmail() : null,
				room.getCleaningStartedAt(),
				room.getCleaningCompletedByUser() != null ? room.getCleaningCompletedByUser().getEmail() : null,
				room.getCleaningCompletedAt(),
				room.getInspectorUser() != null ? room.getInspectorUser().getEmail() : null,
				room.getInspectedAt(),
				room.getUpdatedAt()
		);
	}
}
