package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreateBookingCompanionRequest;
import com.aurora.pms.dto.request.UpdateBookingCompanionRequest;
import com.aurora.pms.dto.response.BookingCompanionResponse;

public interface BookingCompanionService {

	List<BookingCompanionResponse> findAllByBookingId(UUID bookingId);

	BookingCompanionResponse create(UUID bookingId, CreateBookingCompanionRequest request);

	BookingCompanionResponse update(UUID bookingId, UUID companionId, UpdateBookingCompanionRequest request);

	void delete(UUID bookingId, UUID companionId);
}
