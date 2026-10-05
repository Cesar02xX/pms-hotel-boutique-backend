package com.aurora.pms.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.PublicCreateBookingRequest;
import com.aurora.pms.dto.response.PublicAvailabilityResponse;
import com.aurora.pms.dto.response.PublicBookingResponse;
import com.aurora.pms.dto.response.PublicRateResponse;
import com.aurora.pms.dto.response.PublicRoomTypeResponse;

public interface PublicBookingService {

	List<PublicRoomTypeResponse> findRoomTypes();

	List<PublicRateResponse> findRates(UUID roomTypeId);

	PublicAvailabilityResponse findAvailability(
			LocalDate checkIn,
			LocalDate checkOut,
			Integer adults,
			Integer children,
			UUID roomTypeId
	);

	PublicBookingResponse create(PublicCreateBookingRequest request);
}
