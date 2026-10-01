package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.response.HousekeepingRoomResponse;
import com.aurora.pms.dto.response.StayoverCleaningResponse;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;

public interface HousekeepingService {

	List<HousekeepingRoomResponse> findAll(RoomHousekeepingStatus housekeepingStatus);

	HousekeepingRoomResponse findById(UUID roomId);

	HousekeepingRoomResponse startCleaning(UUID roomId, String actorEmail);

	HousekeepingRoomResponse completeCleaning(UUID roomId, String actorEmail);

	HousekeepingRoomResponse inspect(UUID roomId, String actorEmail);

	List<StayoverCleaningResponse> findStayoverCleanings(UUID bookingId);

	StayoverCleaningResponse createStayoverCleaning(UUID roomId, UUID bookingId, String description, String actorEmail);

	StayoverCleaningResponse startStayoverCleaning(UUID requestId, String actorEmail);

	StayoverCleaningResponse completeStayoverCleaning(UUID requestId, String actorEmail);
}
