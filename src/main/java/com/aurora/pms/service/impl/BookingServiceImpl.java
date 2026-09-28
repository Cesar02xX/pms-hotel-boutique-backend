package com.aurora.pms.service.impl;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateBookingRequest;
import com.aurora.pms.dto.request.UpdateBookingRequest;
import com.aurora.pms.dto.response.BookingResponse;
import com.aurora.pms.dto.response.CheckInResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.BookingMapper;
import com.aurora.pms.mapper.CheckInMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.BookingCompanion;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.GuestType;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.RoomStatus;
import com.aurora.pms.repository.BookingCompanionRepository;
import com.aurora.pms.repository.BookingRepository;
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
	private final BookingMapper bookingMapper;
	private final BookingCompanionRepository companionRepository;
	private final CheckInMapper checkInMapper;

	public BookingServiceImpl(
			BookingRepository bookingRepository,
			GuestRepository guestRepository,
			RoomTypeRepository roomTypeRepository,
			RoomRepository roomRepository,
			RateRepository rateRepository,
			BookingMapper bookingMapper,
			BookingCompanionRepository companionRepository,
			CheckInMapper checkInMapper
	) {
		this.bookingRepository = bookingRepository;
		this.guestRepository = guestRepository;
		this.roomTypeRepository = roomTypeRepository;
		this.roomRepository = roomRepository;
		this.rateRepository = rateRepository;
		this.bookingMapper = bookingMapper;
		this.companionRepository = companionRepository;
		this.checkInMapper = checkInMapper;
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
		Room room = request.roomId() != null ? getRoom(request.roomId()) : null;
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
	public CheckInResponse checkIn(UUID id) {
		Booking booking = getBooking(id);
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
	public BookingResponse update(UUID id, UpdateBookingRequest request) {
		Booking booking = getBooking(id);

		if (request.guestId() != null) {
			booking.setGuest(getGuest(request.guestId()));
		}
		if (request.roomTypeId() != null) {
			booking.setRoomType(getRoomType(request.roomTypeId()));
		}
		if (request.roomId() != null) {
			booking.setRoom(getRoom(request.roomId()));
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

	private Room getAssignedRoom(Booking booking) {
		if (booking.getRoom() == null) {
			throw new BadRequestException("Booking must have an assigned room before check-in");
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
			java.time.LocalDate checkIn,
			java.time.LocalDate checkOut,
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
		if (rate != null && !rate.getRoomType().getId().equals(roomType.getId())) {
			throw new BadRequestException("Rate does not belong to room type: " + roomType.getId());
		}
	}

	private void validateRoomAvailability(UUID bookingId, Room room, java.time.LocalDate checkIn,
			java.time.LocalDate checkOut) {
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
		LocalDate today = LocalDate.now();
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

	private long calculateTotalAmountCents(Rate rate, java.time.LocalDate checkIn, java.time.LocalDate checkOut) {
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
