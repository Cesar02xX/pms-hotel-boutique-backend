package com.aurora.pms.service.impl;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateConciergeRequestRequest;
import com.aurora.pms.dto.request.CreateGuestRoomServiceOrderRequest;
import com.aurora.pms.dto.request.CreateGuestServiceRequest;
import com.aurora.pms.dto.request.CreateRoomServiceOrderRequest;
import com.aurora.pms.dto.request.UpdateConciergeRequestStatusRequest;
import com.aurora.pms.dto.response.ConciergeRequestResponse;
import com.aurora.pms.dto.response.GuestLinkResponse;
import com.aurora.pms.dto.response.GuestStayResponse;
import com.aurora.pms.dto.response.RoomServiceOrderResponse;
import com.aurora.pms.dto.response.StayoverCleaningResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.GuestAccount;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.OrderStatus;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.GuestAccountRepository;
import com.aurora.pms.repository.ServiceRequestRepository;
import com.aurora.pms.security.GuestPrincipal;
import com.aurora.pms.security.JwtService;
import com.aurora.pms.service.ConciergeRequestService;
import com.aurora.pms.service.GuestAccessService;
import com.aurora.pms.service.GuestNotificationService;
import com.aurora.pms.service.HousekeepingService;
import com.aurora.pms.service.RoomServiceOrderService;

@Service
public class GuestAccessServiceImpl implements GuestAccessService {

	private static final EnumSet<BookingStatus> LINKABLE_STATUSES = EnumSet.of(BookingStatus.checked_in);
	private static final ZoneId HOTEL_ZONE = ZoneId.of("America/Guatemala");
	private static final EnumSet<ServiceRequestStatus> GUEST_CANCELLABLE_REQUESTS =
			EnumSet.of(ServiceRequestStatus.pending, ServiceRequestStatus.accepted);

	private final BookingRepository bookingRepository;
	private final GuestAccountRepository guestAccountRepository;
	private final ServiceRequestRepository serviceRequestRepository;
	private final RoomServiceOrderService roomServiceOrderService;
	private final HousekeepingService housekeepingService;
	private final ConciergeRequestService conciergeRequestService;
	private final JwtService jwtService;
	private final GuestNotificationService guestNotificationService;

	public GuestAccessServiceImpl(
			BookingRepository bookingRepository,
			GuestAccountRepository guestAccountRepository,
			ServiceRequestRepository serviceRequestRepository,
			RoomServiceOrderService roomServiceOrderService,
			HousekeepingService housekeepingService,
			ConciergeRequestService conciergeRequestService,
			JwtService jwtService,
			GuestNotificationService guestNotificationService
	) {
		this.bookingRepository = bookingRepository;
		this.guestAccountRepository = guestAccountRepository;
		this.serviceRequestRepository = serviceRequestRepository;
		this.roomServiceOrderService = roomServiceOrderService;
		this.housekeepingService = housekeepingService;
		this.conciergeRequestService = conciergeRequestService;
		this.jwtService = jwtService;
		this.guestNotificationService = guestNotificationService;
	}

	@Override
	@Transactional(readOnly = true)
	public GuestLinkResponse link(String code) {
		Booking booking = bookingRepository.findByGuestLinkCode(normalizeCode(code))
				.orElseThrow(() -> new BadRequestException("Guest link code is invalid"));
		if (!LINKABLE_STATUSES.contains(booking.getStatus())) {
			throw new BadRequestException("Guest link code is not usable for booking status " + booking.getStatus());
		}
		LocalDate today = LocalDate.now(HOTEL_ZONE);
		if (today.isBefore(booking.getCheckIn()) || !today.isBefore(booking.getCheckOut())) {
			throw new BadRequestException("Guest link code is expired or not yet active");
		}

		GuestPrincipal principal = new GuestPrincipal(booking.getId(), booking.getGuest().getId(), booking.getGuestLinkCode());
		return new GuestLinkResponse(jwtService.generateGuestAccessToken(principal), "Bearer",
				jwtService.getAccessExpirationSeconds());
	}

	@Override
	@Transactional(readOnly = true)
	public GuestStayResponse getStay(UUID bookingId) {
		Booking booking = getOwnBooking(bookingId);
		GuestAccount account = guestAccountRepository.findByBookingId(bookingId).orElse(null);
		Room room = booking.getRoom();
		return new GuestStayResponse(
				booking.getId(),
				booking.getGuest().getId(),
				booking.getGuest().getFirstName(),
				booking.getGuest().getLastName(),
				room != null ? room.getId() : null,
				room != null ? room.getRoomNumber() : null,
				booking.getRoomType().getId(),
				booking.getRoomType().getName(),
				booking.getCheckIn(),
				booking.getCheckOut(),
				booking.getStatus(),
				account != null ? account.getBalanceCents() : null,
				account != null ? account.getCurrency() : booking.getCurrency()
		);
	}

	@Override
	public RoomServiceOrderResponse createRoomServiceOrder(UUID bookingId, CreateGuestRoomServiceOrderRequest request) {
		return roomServiceOrderService.createOrder(new CreateRoomServiceOrderRequest(bookingId, request.notes(), request.items()));
	}

	@Override
	public List<RoomServiceOrderResponse> findRoomServiceOrders(UUID bookingId) {
		getOwnBooking(bookingId);
		return roomServiceOrderService.findOrders(bookingId, null);
	}

	@Override
	public RoomServiceOrderResponse findRoomServiceOrder(UUID bookingId, UUID orderId) {
		RoomServiceOrderResponse order = roomServiceOrderService.findOrderById(orderId);
		ensureOwn(bookingId, order.bookingId());
		return order;
	}

	@Override
	public RoomServiceOrderResponse cancelRoomServiceOrder(UUID bookingId, UUID orderId) {
		findRoomServiceOrder(bookingId, orderId);
		return roomServiceOrderService.updateStatus(orderId, OrderStatus.cancelled, null);
	}

	@Override
	public StayoverCleaningResponse createHousekeepingRequest(UUID bookingId, CreateGuestServiceRequest request) {
		Booking booking = getOwnBooking(bookingId);
		if (booking.getRoom() == null) {
			throw new BadRequestException("Booking has no assigned room");
		}
		return housekeepingService.createStayoverCleaning(
				booking.getRoom().getId(),
				bookingId,
				request.description(),
				null
		);
	}

	@Override
	public List<StayoverCleaningResponse> findHousekeepingRequests(UUID bookingId) {
		getOwnBooking(bookingId);
		return housekeepingService.findStayoverCleanings(bookingId);
	}

	@Override
	@Transactional
	public StayoverCleaningResponse cancelHousekeepingRequest(UUID bookingId, UUID requestId) {
		ServiceRequest request = serviceRequestRepository.findByIdAndTypeForUpdate(requestId, ServiceRequestType.housekeeping)
				.orElseThrow(() -> new ResourceNotFoundException("Stayover cleaning not found: " + requestId));
		ensureOwn(bookingId, request.getBooking().getId());
		if (!GUEST_CANCELLABLE_REQUESTS.contains(request.getStatus())) {
			throw new BadRequestException("Cannot cancel housekeeping request from status " + request.getStatus());
		}
		request.setStatus(ServiceRequestStatus.cancelled);
		request.setUpdatedAt(OffsetDateTime.now());
		serviceRequestRepository.save(request);
		guestNotificationService.createIfAbsent(
				request.getBooking(),
				"housekeeping_cancelled",
				"Housekeeping",
				"Your housekeeping request was cancelled",
				"housekeeping_request",
				request.getId()
		);
		return housekeepingService.findStayoverCleanings(bookingId).stream()
				.filter(response -> response.id().equals(requestId))
				.findFirst()
				.orElseThrow(() -> new ResourceNotFoundException("Stayover cleaning not found: " + requestId));
	}

	@Override
	public ConciergeRequestResponse createConciergeRequest(UUID bookingId, CreateGuestServiceRequest request) {
		getOwnBooking(bookingId);
		return conciergeRequestService.create(new CreateConciergeRequestRequest(
				bookingId,
				request.description(),
				request.notes()
		));
	}

	@Override
	public List<ConciergeRequestResponse> findConciergeRequests(UUID bookingId) {
		getOwnBooking(bookingId);
		return conciergeRequestService.findAll(bookingId, null);
	}

	@Override
	public ConciergeRequestResponse findConciergeRequest(UUID bookingId, UUID requestId) {
		ConciergeRequestResponse response = conciergeRequestService.findById(requestId);
		ensureOwn(bookingId, response.bookingId());
		return response;
	}

	@Override
	public ConciergeRequestResponse cancelConciergeRequest(UUID bookingId, UUID requestId) {
		findConciergeRequest(bookingId, requestId);
		return conciergeRequestService.updateStatus(requestId,
				new UpdateConciergeRequestStatusRequest(ServiceRequestStatus.cancelled, null, null));
	}

	private Booking getOwnBooking(UUID bookingId) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
		ensureOwn(bookingId, booking.getId());
		return booking;
	}

	private static void ensureOwn(UUID expectedBookingId, UUID actualBookingId) {
		if (!expectedBookingId.equals(actualBookingId)) {
			throw new AccessDeniedException("Guest cannot access this resource");
		}
	}

	private static String normalizeCode(String code) {
		return code == null ? "" : code.trim().toUpperCase();
	}
}
