package com.aurora.pms.dto.request;

import java.util.UUID;

import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.RoomStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRoomRequest(
		@NotBlank(message = "Room number is required")
		String roomNumber,

		@NotNull(message = "Room type id is required")
		UUID roomTypeId,

		Integer floor,

		RoomStatus status,

		RoomHousekeepingStatus housekeepingStatus,

		String notes
) {
}
