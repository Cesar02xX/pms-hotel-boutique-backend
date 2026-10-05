package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CancelBookingRequest;
import com.aurora.pms.dto.request.CreateBookingRequest;
import com.aurora.pms.dto.request.UpdateBookingRequest;
import com.aurora.pms.dto.response.BookingResponse;
import com.aurora.pms.dto.response.CheckInResponse;

public interface BookingService {

	List<BookingResponse> findAll();

	BookingResponse findById(UUID id);

	BookingResponse create(CreateBookingRequest request);

	BookingResponse update(UUID id, UpdateBookingRequest request);

	BookingResponse confirm(UUID id);

	BookingResponse cancel(UUID id, CancelBookingRequest request);

	CheckInResponse checkIn(UUID id);

	BookingResponse checkOut(UUID id);
}
