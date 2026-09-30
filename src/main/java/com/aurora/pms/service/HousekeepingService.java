package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.response.HousekeepingRoomResponse;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;

public interface HousekeepingService {

	List<HousekeepingRoomResponse> findAll(RoomHousekeepingStatus housekeepingStatus);

	HousekeepingRoomResponse findById(UUID roomId);

	HousekeepingRoomResponse startCleaning(UUID roomId);

	HousekeepingRoomResponse completeCleaning(UUID roomId);

	HousekeepingRoomResponse inspect(UUID roomId);
}
