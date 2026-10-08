package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreateHousekeepingChecklistRequest;
import com.aurora.pms.dto.request.UpdateHousekeepingChecklistRequest;
import com.aurora.pms.dto.request.UpdateHousekeepingChecklistTemplateRequest;
import com.aurora.pms.dto.response.HousekeepingChecklistResponse;
import com.aurora.pms.dto.response.HousekeepingChecklistTemplateResponse;
import com.aurora.pms.dto.response.HousekeepingRoomResponse;
import com.aurora.pms.dto.response.StayoverCleaningResponse;
import com.aurora.pms.model.enums.HousekeepingChecklistStatus;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.ServiceRequestStatus;

public interface HousekeepingService {

	List<HousekeepingRoomResponse> findAll(RoomHousekeepingStatus housekeepingStatus);

	HousekeepingRoomResponse findById(UUID roomId);

	HousekeepingRoomResponse startCleaning(UUID roomId, String actorEmail);

	HousekeepingRoomResponse completeCleaning(UUID roomId, String actorEmail);

	HousekeepingRoomResponse inspect(UUID roomId, String actorEmail);

	/** Filtros opcionales: un parametro null no filtra. */
	List<StayoverCleaningResponse> findStayoverCleanings(UUID bookingId, ServiceRequestStatus status);

	StayoverCleaningResponse createStayoverCleaning(UUID roomId, UUID bookingId, String description, String actorEmail);

	StayoverCleaningResponse startStayoverCleaning(UUID requestId, String actorEmail);

	StayoverCleaningResponse completeStayoverCleaning(UUID requestId, String actorEmail);

	List<HousekeepingChecklistResponse> findChecklists(
			UUID roomId,
			HousekeepingChecklistStatus status,
			UUID responsibleUserId
	);

	HousekeepingChecklistResponse createChecklist(CreateHousekeepingChecklistRequest request, String actorEmail);

	HousekeepingChecklistResponse updateChecklist(
			UUID id,
			UpdateHousekeepingChecklistRequest request,
			String actorEmail
	);

	HousekeepingChecklistTemplateResponse getGuestCleaningChecklistTemplate();

	HousekeepingChecklistTemplateResponse updateGuestCleaningChecklistTemplate(
			UpdateHousekeepingChecklistTemplateRequest request
	);
}
