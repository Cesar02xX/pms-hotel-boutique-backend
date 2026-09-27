package com.aurora.pms.dto.request;

import java.util.UUID;

import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.RoomStatus;

import jakarta.validation.constraints.Pattern;

public record UpdateRoomRequest(
		@Pattern(regexp = ".*\\S.*", message = "Room number must not be blank")
		String roomNumber,

		UUID roomTypeId,

		Integer floor,

		RoomStatus status,

		RoomHousekeepingStatus housekeepingStatus,

		String notes
) {
}
