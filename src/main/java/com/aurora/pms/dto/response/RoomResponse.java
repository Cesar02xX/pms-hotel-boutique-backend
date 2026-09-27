package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.RoomStatus;

public record RoomResponse(
		UUID id,
		String roomNumber,
		UUID roomTypeId,
		Integer floor,
		RoomStatus status,
		RoomHousekeepingStatus housekeepingStatus,
		String notes,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
