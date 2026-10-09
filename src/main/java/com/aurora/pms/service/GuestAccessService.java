package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreateGuestBookingRequest;
import com.aurora.pms.dto.request.CreateGuestRoomServiceOrderRequest;
import com.aurora.pms.dto.request.CreateGuestServiceRequest;
import com.aurora.pms.dto.request.GuestLoginRequest;
import com.aurora.pms.dto.request.GuestRegistrationRequest;
import com.aurora.pms.dto.response.BookingResponse;
import com.aurora.pms.dto.response.ConciergeRequestResponse;
import com.aurora.pms.dto.response.GuestLinkResponse;
import com.aurora.pms.dto.response.GuestLoginResponse;
import com.aurora.pms.dto.response.GuestStayResponse;
import com.aurora.pms.dto.response.RoomServiceOrderResponse;
import com.aurora.pms.dto.response.StayoverCleaningResponse;
import com.aurora.pms.model.enums.OrderStatus;
import com.aurora.pms.model.enums.ServiceRequestStatus;

public interface GuestAccessService {

	GuestLoginResponse login(GuestLoginRequest request);

	GuestLinkResponse register(GuestRegistrationRequest request);

	GuestLinkResponse link(String code);

	GuestStayResponse getStay(UUID bookingId);

	RoomServiceOrderResponse createRoomServiceOrder(UUID bookingId, CreateGuestRoomServiceOrderRequest request);

	List<RoomServiceOrderResponse> findRoomServiceOrders(UUID bookingId);

	RoomServiceOrderResponse findRoomServiceOrder(UUID bookingId, UUID orderId);

	RoomServiceOrderResponse cancelRoomServiceOrder(UUID bookingId, UUID orderId);

	StayoverCleaningResponse createHousekeepingRequest(UUID bookingId, CreateGuestServiceRequest request);

	List<StayoverCleaningResponse> findHousekeepingRequests(UUID bookingId);

	StayoverCleaningResponse cancelHousekeepingRequest(UUID bookingId, UUID requestId);

	ConciergeRequestResponse createConciergeRequest(UUID bookingId, CreateGuestServiceRequest request);

	List<ConciergeRequestResponse> findConciergeRequests(UUID bookingId);

	ConciergeRequestResponse findConciergeRequest(UUID bookingId, UUID requestId);

	ConciergeRequestResponse cancelConciergeRequest(UUID bookingId, UUID requestId);

	List<BookingResponse> findBookings(UUID guestId);

	BookingResponse findBookingById(UUID guestId, UUID bookingId);

	BookingResponse createBooking(UUID guestId, CreateGuestBookingRequest request);
}


