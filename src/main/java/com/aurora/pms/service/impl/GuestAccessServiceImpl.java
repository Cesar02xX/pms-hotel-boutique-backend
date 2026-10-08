package com.aurora.pms.service.impl;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;

import com.aurora.pms.dto.request.CreateBookingRequest;
import com.aurora.pms.dto.request.CreateGuestBookingRequest;
import com.aurora.pms.dto.request.CreateConciergeRequestRequest;
import com.aurora.pms.dto.request.CreateGuestRoomServiceOrderRequest;
import com.aurora.pms.dto.request.CreateGuestHousekeepingItemRequest;
import com.aurora.pms.dto.request.CreateGuestServiceRequest;
import com.aurora.pms.dto.request.CreateRoomServiceOrderRequest;
import com.aurora.pms.dto.request.GuestLoginRequest;
import com.aurora.pms.dto.request.GuestRegistrationRequest;
import com.aurora.pms.dto.request.UpdateConciergeRequestStatusRequest;
import com.aurora.pms.dto.response.BookingResponse;
import com.aurora.pms.dto.response.ConciergeRequestResponse;
import com.aurora.pms.dto.response.GuestLinkResponse;
import com.aurora.pms.dto.response.GuestLoginResponse;
import com.aurora.pms.dto.response.GuestStayResponse;
import com.aurora.pms.dto.response.RoomServiceOrderResponse;
import com.aurora.pms.dto.response.StayoverCleaningResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.BookingMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.GuestAccount;
import com.aurora.pms.model.GuestCredential;
import com.aurora.pms.model.InventoryItem;
import com.aurora.pms.model.InventoryMovement;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.InventoryMovementReason;
import com.aurora.pms.model.enums.InventoryMovementType;
import com.aurora.pms.model.enums.OrderStatus;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.GuestAccountRepository;
import com.aurora.pms.repository.GuestCredentialRepository;
import com.aurora.pms.repository.InventoryItemRepository;
import com.aurora.pms.repository.RoomTypeRepository;
import com.aurora.pms.repository.ServiceRequestRepository;
import com.aurora.pms.security.GuestPrincipal;
import com.aurora.pms.security.JwtService;
import com.aurora.pms.service.BookingService;
import com.aurora.pms.service.ConciergeRequestService;
import com.aurora.pms.service.GuestAccessService;
import com.aurora.pms.service.GuestNotificationService;
import com.aurora.pms.service.HousekeepingService;
import com.aurora.pms.service.RoomServiceOrderService;
import com.aurora.pms.service.impl.InventoryStockLedger;

@Service
public class GuestAccessServiceImpl implements GuestAccessService {

	private static final EnumSet<BookingStatus> PORTAL_ACCESS_STATUSES = EnumSet.of(
			BookingStatus.pending,
			BookingStatus.confirmed,
			BookingStatus.checked_in
	);
	private static final ZoneId HOTEL_ZONE = ZoneId.of("America/Guatemala");
	private static final EnumSet<ServiceRequestStatus> GUEST_CANCELLABLE_REQUESTS =
			EnumSet.of(ServiceRequestStatus.pending, ServiceRequestStatus.accepted);

	private static final int MAX_STAY_NIGHTS = 30;
	private static final String NO_AVAILABILITY_MESSAGE = "No availability for the requested room type and dates";

	private final BookingRepository bookingRepository;
	private final GuestAccountRepository guestAccountRepository;
	private final GuestCredentialRepository guestCredentialRepository;
	private final InventoryItemRepository inventoryItemRepository;
	private final InventoryStockLedger stockLedger;
	private final ServiceRequestRepository serviceRequestRepository;
	private final RoomServiceOrderService roomServiceOrderService;
	private final HousekeepingService housekeepingService;
	private final ConciergeRequestService conciergeRequestService;
	private final JwtService jwtService;
	private final GuestNotificationService guestNotificationService;
	private final PasswordEncoder passwordEncoder;
	private final RoomTypeRepository roomTypeRepository;
	private final RoomTypeAvailability roomTypeAvailability;
	private final BookingService bookingService;
	private final BookingMapper bookingMapper;
	private final Clock clock;
	private final ZoneId hotelZoneId;

	public GuestAccessServiceImpl(
			BookingRepository bookingRepository,
			GuestAccountRepository guestAccountRepository,
			GuestCredentialRepository guestCredentialRepository,
			InventoryItemRepository inventoryItemRepository,
			InventoryStockLedger stockLedger,
			ServiceRequestRepository serviceRequestRepository,
			RoomServiceOrderService roomServiceOrderService,
			HousekeepingService housekeepingService,
			ConciergeRequestService conciergeRequestService,
			JwtService jwtService,
			GuestNotificationService guestNotificationService,
			PasswordEncoder passwordEncoder,
			RoomTypeRepository roomTypeRepository,
			RoomTypeAvailability roomTypeAvailability,
			BookingService bookingService,
			BookingMapper bookingMapper,
			Clock clock,
			@Value("${pms.hotel.zone-id}") String hotelZoneId
	) {
		this.bookingRepository = bookingRepository;
		this.guestAccountRepository = guestAccountRepository;
		this.guestCredentialRepository = guestCredentialRepository;
		this.inventoryItemRepository = inventoryItemRepository;
		this.stockLedger = stockLedger;
		this.serviceRequestRepository = serviceRequestRepository;
		this.roomServiceOrderService = roomServiceOrderService;
		this.housekeepingService = housekeepingService;
		this.conciergeRequestService = conciergeRequestService;
		this.jwtService = jwtService;
		this.guestNotificationService = guestNotificationService;
		this.passwordEncoder = passwordEncoder;
		this.roomTypeRepository = roomTypeRepository;
		this.roomTypeAvailability = roomTypeAvailability;
		this.bookingService = bookingService;
		this.bookingMapper = bookingMapper;
		this.clock = clock;
		this.hotelZoneId = ZoneId.of(hotelZoneId);
	}

	@Override
	@Transactional(readOnly = true)
	public GuestLoginResponse login(GuestLoginRequest request) {
		String email = normalizeEmail(request.email());
		GuestCredential credential = guestCredentialRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

		if (!Boolean.TRUE.equals(credential.getActive())
				|| !passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
			throw new BadCredentialsException("Invalid email or password");
		}

		List<Booking> checkedInBookings = bookingRepository.findByGuestIdAndStatusOrderByCheckInDesc(
				credential.getGuest().getId(),
				BookingStatus.checked_in
		);

		if (checkedInBookings.isEmpty()) {
			throw new BadRequestException("Guest has no active stay");
		}

		LocalDate today = LocalDate.now(HOTEL_ZONE);
		Booking activeBooking = checkedInBookings.stream()
				.filter(b -> !today.isBefore(b.getCheckIn()) && today.isBefore(b.getCheckOut()))
				.findFirst()
				.orElseThrow(() -> new BadRequestException("La estancia no está activa: ya venció o todavía no ha comenzado."));

		GuestPrincipal principal = new GuestPrincipal(
				activeBooking.getId(),
				credential.getGuest().getId(),
				activeBooking.getGuestLinkCode()
		);

		return new GuestLoginResponse(
				jwtService.generateGuestAccessToken(principal),
				"Bearer",
				jwtService.getAccessExpirationSeconds()
		);
	}

	@Override
	@Transactional
	public GuestLinkResponse register(GuestRegistrationRequest request) {
		String email = normalizeEmail(request.email());
		Booking booking = findPortalBooking(request.code());
		if (!email.equals(normalizeEmail(booking.getGuest().getEmail()))) {
			throw new BadRequestException("Email does not match the reservation guest");
		}
		if (guestCredentialRepository.findByGuestId(booking.getGuest().getId()).isPresent()
				|| guestCredentialRepository.existsByEmailIgnoreCase(email)) {
			throw new ConflictException("A guest account already exists for this reservation or email");
		}

		GuestCredential credential = new GuestCredential();
		credential.setGuest(booking.getGuest());
		credential.setEmail(email);
		credential.setPasswordHash(passwordEncoder.encode(request.password()));
		credential.setActive(true);
		guestCredentialRepository.save(credential);
		return guestToken(booking);
	}

	@Override
	@Transactional(readOnly = true)
	public GuestLinkResponse link(String code) {
		return guestToken(findPortalBooking(code));
	}

	private Booking findPortalBooking(String code) {
		String normalizedCode = normalizeCode(code);
		Booking booking = bookingRepository.findByGuestLinkCodeIgnoreCase(normalizedCode)
				.or(() -> bookingRepository.findByConfirmationCodeIgnoreCase(normalizedCode))
				.orElseThrow(() -> new BadRequestException("Reservation code is invalid"));
		if (!PORTAL_ACCESS_STATUSES.contains(booking.getStatus())) {
			throw new BadRequestException("Reservation is not available for guest portal access");
		}
		LocalDate today = today();
		if (!today.isBefore(booking.getCheckOut())
				|| (booking.getStatus() == BookingStatus.checked_in && today.isBefore(booking.getCheckIn()))) {
			throw new BadRequestException("Reservation code is expired or not yet active");
		}
		return booking;
	}

	private GuestLinkResponse guestToken(Booking booking) {
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
		requireActiveStay(bookingId);
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
		requireActiveStay(bookingId);
		findRoomServiceOrder(bookingId, orderId);
		return roomServiceOrderService.updateStatus(orderId, OrderStatus.cancelled, null);
	}

	@Override
	public StayoverCleaningResponse createHousekeepingRequest(UUID bookingId, CreateGuestServiceRequest request) {
		Booking booking = requireActiveStay(bookingId);
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
	@Transactional
	public StayoverCleaningResponse createHousekeepingItemRequest(
			UUID bookingId,
			CreateGuestHousekeepingItemRequest request
	) {
		Booking booking = getOwnBooking(bookingId);
		if (booking.getRoom() == null) {
			throw new BadRequestException("Booking has no assigned room");
		}
		InventoryItem item = inventoryItemRepository.findByIdForUpdate(request.itemId())
				.orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + request.itemId()));
		if (!Boolean.TRUE.equals(item.getActive()) || !"housekeeping".equalsIgnoreCase(item.getCategory())) {
			throw new BadRequestException("The selected item is not available for guest housekeeping requests");
		}
		if (item.getCurrentQuantity() < request.quantity()) {
			throw new BadRequestException(
					"Not enough housekeeping items are currently available: " + item.getCurrentQuantity());
		}

		String description = "Artículos solicitados: " + request.quantity() + " × " + item.getName();
		if (request.notes() != null && !request.notes().isBlank()) {
			description += " · " + request.notes().trim();
		}
		StayoverCleaningResponse response = housekeepingService.createStayoverCleaning(
				booking.getRoom().getId(), bookingId, description, null);
		ServiceRequest serviceRequest = serviceRequestRepository.findByIdForUpdate(response.id())
				.orElseThrow(() -> new ResourceNotFoundException("Housekeeping request not found: " + response.id()));
		serviceRequest.setInventoryItem(item);
		serviceRequest.setInventoryQuantity(request.quantity());
		serviceRequestRepository.save(serviceRequest);

		InventoryMovement movement = new InventoryMovement();
		movement.setType(InventoryMovementType.out);
		movement.setReason(InventoryMovementReason.reservation);
		movement.setQuantity(request.quantity());
		movement.setNotes("Reserva para solicitud de limpieza " + response.id());
		stockLedger.apply(item, movement);
		return response;
	}

	@Override
	public List<StayoverCleaningResponse> findHousekeepingRequests(UUID bookingId) {
		getOwnBooking(bookingId);
		return housekeepingService.findStayoverCleanings(bookingId, null);
	}

	@Override
	@Transactional
	public StayoverCleaningResponse cancelHousekeepingRequest(UUID bookingId, UUID requestId) {
		requireActiveStay(bookingId);
		ServiceRequest request = serviceRequestRepository.findByIdAndTypeForUpdate(requestId, ServiceRequestType.housekeeping)
				.orElseThrow(() -> new ResourceNotFoundException("Stayover cleaning not found: " + requestId));
		ensureOwn(bookingId, request.getBooking().getId());
		if (!GUEST_CANCELLABLE_REQUESTS.contains(request.getStatus())) {
			throw new BadRequestException("Cannot cancel housekeeping request from status " + request.getStatus());
		}
		request.setStatus(ServiceRequestStatus.cancelled);
		request.setUpdatedAt(OffsetDateTime.now());
		serviceRequestRepository.save(request);
		if (request.getInventoryItem() != null && request.getInventoryQuantity() != null) {
			InventoryItem item = inventoryItemRepository.findByIdForUpdate(request.getInventoryItem().getId())
					.orElseThrow(() -> new ResourceNotFoundException(
							"Inventory item not found: " + request.getInventoryItem().getId()));
			InventoryMovement release = new InventoryMovement();
			release.setType(InventoryMovementType.in);
			release.setReason(InventoryMovementReason.reservation_release);
			release.setQuantity(request.getInventoryQuantity());
			release.setNotes("Liberación de reserva de solicitud de limpieza " + requestId);
			stockLedger.apply(item, release);
		}
		guestNotificationService.createIfAbsent(
				request.getBooking(),
				"housekeeping_cancelled",
				"Housekeeping",
				"Your housekeeping request was cancelled",
				"housekeeping_request",
				request.getId()
		);
		return housekeepingService.findStayoverCleanings(bookingId, null).stream()
				.filter(response -> response.id().equals(requestId))
				.findFirst()
				.orElseThrow(() -> new ResourceNotFoundException("Stayover cleaning not found: " + requestId));
	}

	@Override
	public ConciergeRequestResponse createConciergeRequest(UUID bookingId, CreateGuestServiceRequest request) {
		requireActiveStay(bookingId);
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
		requireActiveStay(bookingId);
		findConciergeRequest(bookingId, requestId);
		return conciergeRequestService.updateStatus(requestId,
				new UpdateConciergeRequestStatusRequest(ServiceRequestStatus.cancelled, null, null));
	}

	@Override
	@Transactional(readOnly = true)
	public List<BookingResponse> findBookings(UUID guestId) {
		return bookingRepository.findByGuestIdOrderByCheckInDesc(guestId).stream()
				.map(bookingMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public BookingResponse findBookingById(UUID guestId, UUID bookingId) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
		if (!booking.getGuest().getId().equals(guestId)) {
			throw new ResourceNotFoundException("Booking not found: " + bookingId);
		}
		return bookingMapper.toResponse(booking);
	}

	@Override
	@Transactional
	public BookingResponse createBooking(UUID guestId, CreateGuestBookingRequest request) {
		int nights = validateStay(request.checkIn(), request.checkOut(), request.adults(), request.children());

		// Bloquea el tipo de habitación para serializar reservas concurrentes
		RoomType roomType = getPublicRoomType(
				roomTypeRepository.findByIdForUpdate(request.roomTypeId()), request.roomTypeId());
		if (guestCount(request.adults(), request.children()) > roomType.getCapacity()) {
			throw new BadRequestException("Guest count exceeds room type capacity");
		}

		List<UUID> roomTypeIds = List.of(roomType.getId());
		Rate rate = roomTypeAvailability.ratesCoveringStay(roomTypeIds, request.checkIn(), request.checkOut())
				.get(roomType.getId());
		if (rate == null) {
			throw new BadRequestException("No rate available for the requested stay");
		}
		if (nights < rate.getMinimumNights()) {
			throw new BadRequestException("Stay does not meet the rate minimum nights");
		}
		int available = roomTypeAvailability.availableRooms(roomTypeIds, request.checkIn(), request.checkOut())
				.getOrDefault(roomType.getId(), 0);
		if (available <= 0) {
			throw new ConflictException(NO_AVAILABILITY_MESSAGE);
		}

		return bookingService.create(new CreateBookingRequest(
				guestId,
				roomType.getId(),
				null,
				rate.getId(),
				request.checkIn(),
				request.checkOut(),
				request.adults(),
				request.children(),
				trimToNull(request.notes())
		));
	}

	private int validateStay(LocalDate checkIn, LocalDate checkOut, Integer adults, Integer children) {
		if (checkIn == null) {
			throw new BadRequestException("Check-in date is required");
		}
		if (checkOut == null) {
			throw new BadRequestException("Check-out date is required");
		}
		if (adults == null) {
			throw new BadRequestException("Adults is required");
		}
		if (adults < 1) {
			throw new BadRequestException("Adults must be greater than zero");
		}
		if (children == null || children < 0) {
			throw new BadRequestException("Children must not be negative");
		}
		if (!checkIn.isBefore(checkOut)) {
			throw new BadRequestException("Check-in date must be before check-out date");
		}
		if (checkIn.isBefore(today())) {
			throw new BadRequestException("Check-in date cannot be in the past");
		}
		long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
		if (nights > MAX_STAY_NIGHTS) {
			throw new BadRequestException("Stay cannot exceed " + MAX_STAY_NIGHTS + " nights");
		}
		return (int) nights;
	}

	private static long guestCount(int adults, int children) {
		return (long) adults + children;
	}

	private static RoomType getPublicRoomType(java.util.Optional<RoomType> roomType, UUID roomTypeId) {
		return roomType
				.filter(found -> Boolean.TRUE.equals(found.getActive()))
				.orElseThrow(() -> new BadRequestException("Room type not available: " + roomTypeId));
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(hotelZoneId));
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}


	private Booking getOwnBooking(UUID bookingId) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
		currentGuestPrincipal().ifPresent(guest -> {
			ensureOwn(guest.bookingId(), booking.getId());
			ensureOwn(guest.guestId(), booking.getGuest().getId());
			if (!normalizeCode(guest.linkCode()).equals(normalizeCode(booking.getGuestLinkCode()))) {
				throw new AccessDeniedException("Guest cannot access this resource");
			}
			if (!PORTAL_ACCESS_STATUSES.contains(booking.getStatus()) || !today().isBefore(booking.getCheckOut())) {
				throw new AccessDeniedException("Guest reservation is not available");
			}
		});
		return booking;
	}

	private Booking requireActiveStay(UUID bookingId) {
		Booking booking = getOwnBooking(bookingId);
		LocalDate today = today();
		if (booking.getStatus() != BookingStatus.checked_in
				|| today.isBefore(booking.getCheckIn())
				|| !today.isBefore(booking.getCheckOut())) {
			throw new AccessDeniedException("Guest stay is not active");
		}
		return booking;
	}

	private static java.util.Optional<GuestPrincipal> currentGuestPrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof GuestPrincipal guest) {
			return java.util.Optional.of(guest);
		}
		return java.util.Optional.empty();
	}

	private static void ensureOwn(UUID expectedBookingId, UUID actualBookingId) {
		if (!expectedBookingId.equals(actualBookingId)) {
			throw new AccessDeniedException("Guest cannot access this resource");
		}
	}

	private static String normalizeCode(String code) {
		return code == null ? "" : code.trim().toUpperCase();
	}

	private static String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase();
	}
}
