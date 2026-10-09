package com.aurora.pms.service.impl;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CancelBookingRequest;
import com.aurora.pms.dto.request.CreateBookingRequest;
import com.aurora.pms.dto.request.UpdateBookingRequest;
import com.aurora.pms.dto.response.BookingResponse;
import com.aurora.pms.dto.response.CheckInResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.BookingMapper;
import com.aurora.pms.mapper.CheckInMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.BookingCompanion;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.GuestAccount;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.GuestAccountStatus;
import com.aurora.pms.model.enums.GuestType;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.RoomStatus;
import com.aurora.pms.repository.BookingCompanionRepository;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.GuestAccountRepository;
import com.aurora.pms.repository.GuestRepository;
import com.aurora.pms.repository.RateRepository;
import com.aurora.pms.repository.RoomRepository;
import com.aurora.pms.repository.RoomTypeRepository;
import com.aurora.pms.service.BookingService;

@Service
public class BookingServiceImpl implements BookingService {

	private static final Set<BookingStatus> ACTIVE_ROOM_STATUSES = Set.of(
			BookingStatus.pending,
			BookingStatus.confirmed,
			BookingStatus.checked_in
	);
	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final BookingRepository bookingRepository;
	private final GuestRepository guestRepository;
	private final RoomTypeRepository roomTypeRepository;
	private final RoomRepository roomRepository;
	private final RateRepository rateRepository;
	private final GuestAccountRepository guestAccountRepository;
	private final BookingMapper bookingMapper;
	private final BookingCompanionRepository companionRepository;
	private final CheckInMapper checkInMapper;
	private final Clock clock;
	private final ZoneId hotelZoneId;

	public BookingServiceImpl(
			BookingRepository bookingRepository,
			GuestRepository guestRepository,
			RoomTypeRepository roomTypeRepository,
			RoomRepository roomRepository,
			RateRepository rateRepository,
			GuestAccountRepository guestAccountRepository,
			BookingMapper bookingMapper,
			BookingCompanionRepository companionRepository,
			CheckInMapper checkInMapper,
			Clock clock,
			@Value("${pms.hotel.zone-id}") String hotelZoneId
	) {
		this.bookingRepository = bookingRepository;
		this.guestRepository = guestRepository;
		this.roomTypeRepository = roomTypeRepository;
		this.roomRepository = roomRepository;
		this.rateRepository = rateRepository;
		this.guestAccountRepository = guestAccountRepository;
		this.bookingMapper = bookingMapper;
		this.companionRepository = companionRepository;
		this.checkInMapper = checkInMapper;
		this.clock = clock;
		this.hotelZoneId = ZoneId.of(hotelZoneId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<BookingResponse> findAll() {
		return bookingRepository.findAll(Sort.by("checkIn", "createdAt")).stream()
				.map(bookingMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public BookingResponse findById(UUID id) {
		return bookingMapper.toResponse(getBooking(id));
	}

	@Override
	@Transactional
	public BookingResponse create(CreateBookingRequest request) {
		Guest guest = getGuest(request.guestId());
		RoomType roomType = getRoomType(request.roomTypeId());
		Room room = request.roomId() != null ? getRoomForUpdate(request.roomId()) : null;
		Rate rate = request.rateId() != null ? getRate(request.rateId()) : null;

		validateBookingState(roomType, room, rate, request.checkIn(), request.checkOut(), request.adults(),
				request.children());
		validateRoomAvailability(null, room, request.checkIn(), request.checkOut());

		Booking booking = bookingMapper.toEntity(request, guest, roomType, room, rate);
		booking.setConfirmationCode(generateConfirmationCode());
		booking.setGuestLinkCode(generateGuestLinkCode());
		booking.setTotalAmountCents(calculateTotalAmountCents(rate, request.checkIn(), request.checkOut()));
		OffsetDateTime now = OffsetDateTime.now();
		booking.setCreatedAt(now);
		booking.setUpdatedAt(now);

		return bookingMapper.toResponse(bookingRepository.save(booking));
	}

	@Override
	@Transactional
	public BookingResponse confirm(UUID id) {
		Booking booking = getBookingForUpdate(id);
		if (booking.getStatus() != BookingStatus.pending) {
			throw new BadRequestException("Only pending bookings can be confirmed");
		}
		booking.setStatus(BookingStatus.confirmed);
		booking.setUpdatedAt(OffsetDateTime.now(clock));
		return bookingMapper.toResponse(bookingRepository.save(booking));
	}

	@Override
	@Transactional
	public BookingResponse cancel(UUID id, CancelBookingRequest request) {
		Booking booking = getBookingForUpdate(id);
		if (booking.getStatus() != BookingStatus.pending && booking.getStatus() != BookingStatus.confirmed) {
			throw new BadRequestException("Only pending or confirmed bookings can be cancelled");
		}
		OffsetDateTime now = OffsetDateTime.now(clock);
		booking.setStatus(BookingStatus.cancelled);
		booking.setCancellationReason(request.reason().trim());
		booking.setCancelledAt(now);
		booking.setUpdatedAt(now);
		return bookingMapper.toResponse(bookingRepository.save(booking));
	}

	@Override
	@Transactional
	public CheckInResponse checkIn(UUID id) {
		Booking booking = getBookingForCheckIn(id);
		validateCheckInStatus(booking);
		validateCheckInDates(booking);

		Room room = getAssignedRoom(booking);
		validateCheckInRoom(booking, room);
		List<BookingCompanion> companions = companionRepository.findByBookingIdOrderByCreatedAt(booking.getId());
		validateCheckInComposition(booking, companions);

		OffsetDateTime now = OffsetDateTime.now();
		booking.setStatus(BookingStatus.checked_in);
		booking.setUpdatedAt(now);
		room.setStatus(RoomStatus.occupied);
		room.setUpdatedAt(now);

		bookingRepository.save(booking);
		roomRepository.save(room);

		return checkInMapper.toResponse(booking, room, companions.size(), now);
	}

	@Override
	@Transactional
	public BookingResponse checkOut(UUID id) {
		Booking booking = getBookingForCheckIn(id);
		if (booking.getStatus() != BookingStatus.checked_in) {
			throw new BadRequestException("Only checked-in bookings can be checked out");
		}
		Room room = getAssignedRoomForCheckOut(booking);
		GuestAccount account = guestAccountRepository.findByBookingIdForUpdate(id)
				.orElseThrow(() -> GuestAccountBalance.accountNotFound(id));
		if (account.getStatus() != GuestAccountStatus.open) {
			throw new BadRequestException("Guest account is not open");
		}
		if (account.getBalanceCents() != 0L) {
			throw new ConflictException("Guest account balance must be zero before checkout");
		}

		OffsetDateTime now = OffsetDateTime.now(clock);
		account.setStatus(GuestAccountStatus.closed);
		account.setClosedAt(now);
		account.setUpdatedAt(now);
		booking.setStatus(BookingStatus.checked_out);
		booking.setUpdatedAt(now);
		room.setStatus(RoomStatus.available);
		room.setHousekeepingStatus(RoomHousekeepingStatus.dirty);
		room.setUpdatedAt(now);

		guestAccountRepository.save(account);
		roomRepository.save(room);
		return bookingMapper.toResponse(bookingRepository.save(booking));
	}

	@Override
	@Transactional
	public BookingResponse update(UUID id, UpdateBookingRequest request) {
		Booking booking = getBookingForUpdate(id);
		validateGeneralUpdateAllowed(booking, request);

		if (request.guestId() != null) {
			booking.setGuest(getGuest(request.guestId()));
		}
		if (request.roomTypeId() != null) {
			booking.setRoomType(getRoomType(request.roomTypeId()));
		}
		if (request.roomId() != null) {
			booking.setRoom(getRoomForUpdate(request.roomId()));
		}
		if (request.rateId() != null) {
			booking.setRate(getRate(request.rateId()));
		}

		bookingMapper.applyUpdate(booking, request);
		validateBookingState(booking.getRoomType(), booking.getRoom(), booking.getRate(), booking.getCheckIn(),
				booking.getCheckOut(), booking.getAdults(), booking.getChildren());
		validateRoomAvailability(booking.getId(), booking.getRoom(), booking.getCheckIn(), booking.getCheckOut());

		booking.setTotalAmountCents(calculateTotalAmountCents(booking.getRate(), booking.getCheckIn(),
				booking.getCheckOut()));
		booking.setUpdatedAt(OffsetDateTime.now());

		return bookingMapper.toResponse(bookingRepository.save(booking));
	}

	private Booking getBooking(UUID id) {
		return bookingRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
	}

	private Booking getBookingForCheckIn(UUID id) {
		return bookingRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
	}

	private Booking getBookingForUpdate(UUID id) {
		return bookingRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
	}

	private Guest getGuest(UUID id) {
		return guestRepository.findById(id)
				.orElseThrow(() -> new BadRequestException("Guest not found: " + id));
	}

	private RoomType getRoomType(UUID id) {
		return roomTypeRepository.findById(id)
				.orElseThrow(() -> new BadRequestException("Room type not found: " + id));
	}

	private Room getRoom(UUID id) {
		return roomRepository.findById(id)
				.orElseThrow(() -> new BadRequestException("Room not found: " + id));
	}

	private Room getRoomForUpdate(UUID id) {
		return roomRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new BadRequestException("Room not found: " + id));
	}

	private Room getAssignedRoom(Booking booking) {
		if (booking.getRoom() == null) {
			throw new BadRequestException("Booking must have an assigned room before check-in");
		}
		UUID roomId = booking.getRoom().getId();
		return roomRepository.findById(roomId)
				.orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
	}

	private Room getAssignedRoomForCheckOut(Booking booking) {
		if (booking.getRoom() == null) {
			throw new BadRequestException("Booking must have an assigned room before check-out");
		}
		UUID roomId = booking.getRoom().getId();
		return roomRepository.findById(roomId)
				.orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));
	}

	private Rate getRate(UUID id) {
		return rateRepository.findById(id)
				.orElseThrow(() -> new BadRequestException("Rate not found: " + id));
	}

	private void validateBookingState(
			RoomType roomType,
			Room room,
			Rate rate,
			LocalDate checkIn,
			LocalDate checkOut,
			Integer adults,
			Integer children
	) {
		if (checkIn == null || checkOut == null || !checkIn.isBefore(checkOut)) {
			throw new BadRequestException("Check-in date must be before check-out date");
		}
		if (adults == null || adults < 1) {
			throw new BadRequestException("Adults must be greater than zero");
		}
		if (children == null || children < 0) {
			throw new BadRequestException("Children must not be negative");
		}
		if (adults + children > roomType.getCapacity()) {
			throw new BadRequestException("Guest count exceeds room type capacity");
		}
		if (room != null && !room.getRoomType().getId().equals(roomType.getId())) {
			throw new BadRequestException("Room does not belong to room type: " + roomType.getId());
		}
		if (room != null && (room.getStatus() == RoomStatus.maintenance
				|| room.getStatus() == RoomStatus.out_of_service)) {
			throw new BadRequestException("Room is not operable for booking assignment");
		}
		if (rate != null && !rate.getRoomType().getId().equals(roomType.getId())) {
			throw new BadRequestException("Rate does not belong to room type: " + roomType.getId());
		}
		validateRate(rate, checkIn, checkOut);
	}

	private void validateGeneralUpdateAllowed(Booking booking, UpdateBookingRequest request) {
		if (request.status() != null) {
			throw new BadRequestException("Booking status cannot be changed through the general update endpoint");
		}
		if (booking.getStatus() == BookingStatus.checked_in && hasStructuralChanges(request)) {
			throw new ConflictException(
					"Checked-in bookings cannot be structurally modified through the general update endpoint");
		}
	}

	private boolean hasStructuralChanges(UpdateBookingRequest request) {
		return request.guestId() != null
				|| request.roomTypeId() != null
				|| request.roomId() != null
				|| request.rateId() != null
				|| request.checkIn() != null
				|| request.checkOut() != null
				|| request.adults() != null
				|| request.children() != null;
	}

	private void validateRate(Rate rate, LocalDate checkIn, LocalDate checkOut) {
		if (rate == null) {
			return;
		}
		if (!Boolean.TRUE.equals(rate.getActive())) {
			throw new BadRequestException("Rate is not active");
		}
		if (checkIn.isBefore(rate.getValidFrom())) {
			throw new BadRequestException("Rate is not valid for the requested stay dates");
		}
		LocalDate lastNight = checkOut.minusDays(1);
		if (rate.getValidTo() != null && lastNight.isAfter(rate.getValidTo())) {
			throw new BadRequestException("Rate is not valid for the requested stay dates");
		}
		long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
		if (nights < rate.getMinimumNights()) {
			throw new BadRequestException("Stay does not meet the rate minimum nights");
		}
	}

	private void validateRoomAvailability(UUID bookingId, Room room, LocalDate checkIn, LocalDate checkOut) {
		if (room == null) {
			return;
		}

		boolean hasOverlap = bookingId == null
				? bookingRepository.existsActiveOverlap(room.getId(), ACTIVE_ROOM_STATUSES, checkIn, checkOut)
				: bookingRepository.existsActiveOverlapExcludingBooking(bookingId, room.getId(), ACTIVE_ROOM_STATUSES,
						checkIn, checkOut);
		if (hasOverlap) {
			throw new BadRequestException("Room is not available for the requested dates");
		}
	}

	private void validateCheckInStatus(Booking booking) {
		if (booking.getStatus() == BookingStatus.checked_in) {
			throw new BadRequestException("Booking is already checked in");
		}
		if (booking.getStatus() != BookingStatus.confirmed) {
			throw new BadRequestException("Booking status does not allow check-in");
		}
	}

	private void validateCheckInDates(Booking booking) {
		if (booking.getCheckIn() == null || booking.getCheckOut() == null
				|| !booking.getCheckIn().isBefore(booking.getCheckOut())) {
			throw new BadRequestException("Booking dates are invalid");
		}
		LocalDate today = LocalDate.now(clock.withZone(hotelZoneId));
		if (today.isBefore(booking.getCheckIn()) || !today.isBefore(booking.getCheckOut())) {
			throw new BadRequestException("Booking cannot be checked in outside its stay dates");
		}
	}

	private void validateCheckInRoom(Booking booking, Room room) {
		if (!room.getRoomType().getId().equals(booking.getRoomType().getId())) {
			throw new BadRequestException("Room does not belong to booking room type");
		}
		if (room.getStatus() != RoomStatus.available) {
			throw new BadRequestException("Room is not available for check-in");
		}
		if (room.getHousekeepingStatus() != RoomHousekeepingStatus.clean
				&& room.getHousekeepingStatus() != RoomHousekeepingStatus.inspected) {
			throw new BadRequestException("Room is not ready for check-in");
		}
		boolean hasOverlap = bookingRepository.existsActiveOverlapExcludingBooking(booking.getId(), room.getId(),
				ACTIVE_ROOM_STATUSES, booking.getCheckIn(), booking.getCheckOut());
		if (hasOverlap) {
			throw new BadRequestException("Room is not available for the requested dates");
		}
	}

	private void validateCheckInComposition(Booking booking, List<BookingCompanion> companions) {
		long adultCompanions = companions.stream()
				.filter(companion -> companion.getGuestType() == GuestType.adult)
				.count();
		long childCompanions = companions.stream()
				.filter(companion -> companion.getGuestType() == GuestType.child)
				.count();

		long adults = adultCompanions + 1;
		long children = childCompanions;
		long totalOccupants = adults + children;

		if (totalOccupants > booking.getRoomType().getCapacity()) {
			throw new BadRequestException("Booking occupants exceed room type capacity");
		}
		if (adults != booking.getAdults() || children != booking.getChildren()) {
			throw new BadRequestException("Booking companions do not match declared adult and child composition");
		}
	}

	private long calculateTotalAmountCents(Rate rate, LocalDate checkIn, LocalDate checkOut) {
		if (rate == null) {
			return 0L;
		}
		long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
		return rate.getPriceCents() * nights;
	}

	private String generateConfirmationCode() {
		return generateUniqueCode("BKG-", bookingRepository::existsByConfirmationCode);
	}

	private String generateGuestLinkCode() {
		return generateUniqueCode("GL-", bookingRepository::existsByGuestLinkCode);
	}

	private String generateUniqueCode(String prefix, java.util.function.Predicate<String> exists) {
		String code;
		do {
			code = prefix + randomCode(8);
		} while (exists.test(code));
		return code;
	}

	private static String randomCode(int length) {
		StringBuilder builder = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			builder.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
		}
		return builder.toString();
	}
}
