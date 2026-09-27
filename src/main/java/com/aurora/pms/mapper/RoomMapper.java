package com.aurora.pms.mapper;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.CreateRoomRequest;
import com.aurora.pms.dto.request.UpdateRoomRequest;
import com.aurora.pms.dto.response.RoomResponse;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.RoomStatus;

@Component
public class RoomMapper {

	public RoomResponse toResponse(Room room) {
		return new RoomResponse(
				room.getId(),
				room.getRoomNumber(),
				room.getRoomType().getId(),
				room.getFloor(),
				room.getStatus(),
				room.getHousekeepingStatus(),
				room.getNotes(),
				room.getCreatedAt(),
				room.getUpdatedAt()
		);
	}

	public Room toEntity(CreateRoomRequest request, RoomType roomType) {
		Room room = new Room();
		room.setRoomNumber(request.roomNumber().trim());
		room.setRoomType(roomType);
		room.setFloor(request.floor());
		room.setStatus(request.status() != null ? request.status() : RoomStatus.available);
		room.setHousekeepingStatus(request.housekeepingStatus() != null
				? request.housekeepingStatus()
				: RoomHousekeepingStatus.dirty);
		room.setNotes(request.notes());
		return room;
	}

	/**
	 * Aplica solo los campos presentes en el request. La relación con RoomType
	 * la resuelve el servicio, porque requiere validar que exista.
	 */
	public void applyUpdate(Room room, UpdateRoomRequest request) {
		if (request.roomNumber() != null) {
			room.setRoomNumber(request.roomNumber().trim());
		}
		if (request.floor() != null) {
			room.setFloor(request.floor());
		}
		if (request.status() != null) {
			room.setStatus(request.status());
		}
		if (request.housekeepingStatus() != null) {
			room.setHousekeepingStatus(request.housekeepingStatus());
		}
		if (request.notes() != null) {
			room.setNotes(request.notes());
		}
	}
}
