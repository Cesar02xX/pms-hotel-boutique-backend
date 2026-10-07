package com.aurora.pms.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateBookingRequest;
import com.aurora.pms.dto.request.CreateGuestRequest;
import com.aurora.pms.dto.request.PublicCreateBookingRequest;
import com.aurora.pms.dto.request.PublicGuestRequest;
import com.aurora.pms.dto.response.BookingResponse;
import com.aurora.pms.dto.response.MediaImageResponse;
import com.aurora.pms.dto.response.PublicAvailabilityResponse;
import com.aurora.pms.dto.response.PublicAvailabilityResult;
import com.aurora.pms.dto.response.PublicBookingResponse;
import com.aurora.pms.dto.response.PublicRateResponse;
import com.aurora.pms.dto.response.PublicRoomTypeResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.mapper.PublicBookingMapper;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.RoomFeature;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.MediaTarget;
import com.aurora.pms.repository.GuestRepository;
import com.aurora.pms.repository.RateRepository;
import com.aurora.pms.repository.RoomFeatureRepository;
import com.aurora.pms.repository.RoomTypeFeatureRepository;
import com.aurora.pms.repository.RoomTypeRepository;
import com.aurora.pms.service.BookingService;
import com.aurora.pms.service.GuestService;
import com.aurora.pms.service.MediaImageService;
import com.aurora.pms.service.PublicBookingService;

@Service
public class PublicBookingServiceImpl implements PublicBookingService {

	static final int MAX_STAY_NIGHTS = 30;
	static final String GUEST_CONFLICT_MESSAGE =
			"Guest details conflict with an existing guest record. Please contact the hotel to complete the booking";
	static final String NO_AVAILABILITY_MESSAGE = "No availability for the requested room type and dates";

	private final RoomTypeRepository roomTypeRepository;
	private final RoomTypeFeatureRepository roomTypeFeatureRepository;
	private final RoomFeatureRepository roomFeatureRepository;
	private final RateRepository rateRepository;
	private final GuestRepository guestRepository;
	private final RoomTypeAvailability roomTypeAvailability;
	private final GuestService guestService;
	private final BookingService bookingService;
	private final PublicBookingMapper publicBookingMapper;
	private final MediaImageService mediaImageService;
	private final Clock clock;
	private final ZoneId hotelZoneId;

	public PublicBookingServiceImpl(
			RoomTypeRepository roomTypeRepository,
			RoomTypeFeatureRepository roomTypeFeatureRepository,
			RoomFeatureRepository roomFeatureRepository,
			RateRepository rateRepository,
			GuestRepository guestRepository,
			RoomTypeAvailability roomTypeAvailability,
			GuestService guestService,
			BookingService bookingService,
			PublicBookingMapper publicBookingMapper,
			MediaImageService mediaImageService,
			Clock clock,
			@Value("${pms.hotel.zone-id}") String hotelZoneId
	) {
		this.roomTypeRepository = roomTypeRepository;
		this.roomTypeFeatureRepository = roomTypeFeatureRepository;
		this.roomFeatureRepository = roomFeatureRepository;
		this.rateRepository = rateRepository;
		this.guestRepository = guestRepository;
		this.roomTypeAvailability = roomTypeAvailability;
		this.guestService = guestService;
		this.bookingService = bookingService;
		this.publicBookingMapper = publicBookingMapper;
		this.mediaImageService = mediaImageService;
		this.clock = clock;
		this.hotelZoneId = ZoneId.of(hotelZoneId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<PublicRoomTypeResponse> findRoomTypes() {
		List<RoomType> roomTypes = roomTypeRepository.findByActiveTrueOrderByName();
		if (roomTypes.isEmpty()) {
			return List.of();
		}

		List<UUID> roomTypeIds = roomTypes.stream().map(RoomType::getId).toList();
		Map<UUID, List<UUID>> featureIdsByRoomType = roomTypeFeatureRepository.findByIdRoomTypeIdIn(roomTypeIds)
				.stream()
				.collect(Collectors.groupingBy(
						association -> association.getId().getRoomTypeId(),
						Collectors.mapping(association -> association.getId().getRoomFeatureId(), Collectors.toList())
				));
		List<UUID> featureIds = featureIdsByRoomType.values().stream().flatMap(List::stream).distinct().toList();
		Map<UUID, RoomFeature> featuresById = roomFeatureRepository.findAllById(featureIds).stream()
				.collect(Collectors.toMap(RoomFeature::getId, Function.identity()));
		Map<UUID, List<MediaImageResponse>> imagesByRoomType = mediaImageService.findImages(
				MediaTarget.room_type,
				roomTypeIds
		);

		return roomTypes.stream()
				.map(roomType -> publicBookingMapper.toRoomTypeResponse(
						roomType,
						featureIdsByRoomType.getOrDefault(roomType.getId(), List.of()).stream()
								.map(featuresById::get)
								.filter(Objects::nonNull)
								.sorted(Comparator.comparing(RoomFeature::getName))
								.toList(),
						imagesByRoomType.getOrDefault(roomType.getId(), List.of())
				))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<PublicRateResponse> findRates(UUID roomTypeId) {
		return rateRepository.findActiveCurrentForActiveRoomTypes(today()).stream()
				.filter(rate -> roomTypeId == null || rate.getRoomType().getId().equals(roomTypeId))
				.map(publicBookingMapper::toRateResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public PublicAvailabilityResponse findAvailability(
			LocalDate checkIn,
			LocalDate checkOut,
			Integer adults,
			Integer children,
			UUID roomTypeId
	) {
		int childCount = children != null ? children : 0;
		int nights = validateStay(checkIn, checkOut, adults, childCount);
		long guests = guestCount(adults, childCount);

		List<RoomType> candidates = (roomTypeId != null
				? List.of(getPublicRoomType(roomTypeRepository.findById(roomTypeId), roomTypeId))
				: roomTypeRepository.findByActiveTrueOrderByName())
				.stream()
				.filter(roomType -> roomType.getCapacity() >= guests)
				.toList();
		List<UUID> candidateIds = candidates.stream().map(RoomType::getId).toList();

		Map<UUID, Rate> rates = roomTypeAvailability.ratesCoveringStay(candidateIds, checkIn, checkOut);
		Map<UUID, Integer> availableRooms = roomTypeAvailability.availableRooms(candidateIds, checkIn, checkOut);

		List<PublicAvailabilityResult> results = candidates.stream()
				.filter(roomType -> {
					Rate rate = rates.get(roomType.getId());
					return rate != null && nights >= rate.getMinimumNights()
							&& availableRooms.getOrDefault(roomType.getId(), 0) > 0;
				})
				.map(roomType -> publicBookingMapper.toAvailabilityResult(
						roomType,
						availableRooms.get(roomType.getId()),
						rates.get(roomType.getId()),
						nights
				))
				.toList();

		return new PublicAvailabilityResponse(checkIn, checkOut, nights, adults, childCount, results);
	}

	@Override
	@Transactional
	public PublicBookingResponse create(PublicCreateBookingRequest request) {
		int nights = validateStay(request.checkIn(), request.checkOut(), request.adults(), request.children());

		// Bloquea el tipo de habitación: dos reservas públicas simultáneas del mismo
		// tipo se serializan y la segunda ya ve la primera al contar disponibilidad.
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

		UUID guestId = resolveGuestId(request.guest());
		BookingResponse booking = bookingService.create(new CreateBookingRequest(
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

		// Los datos del huésped en la respuesta salen de la petición, nunca del
		// registro guardado: un huésped reutilizado no expone lo que ya tenía.
		return publicBookingMapper.toBookingResponse(booking, roomType, rate, request.guest(), nights);
	}

	/**
	 * Reutiliza un huésped solo si email y documento coinciden con el mismo
	 * registro, sin modificar sus datos. Una coincidencia parcial responde 409 con
	 * un mensaje que no indica cuál de los dos datos ya existe.
	 */
	private UUID resolveGuestId(PublicGuestRequest request) {
		String email = request.email().trim();
		String documentNumber = trimToNull(request.documentNumber());
		boolean hasDocument = request.documentType() != null && documentNumber != null;

		Optional<Guest> byEmail = guestRepository.findByEmailIgnoreCase(email);
		Optional<Guest> byDocument = hasDocument
				? guestRepository.findByDocumentTypeAndDocumentNumber(request.documentType(), documentNumber)
				: Optional.empty();

		if (byEmail.isEmpty() && byDocument.isEmpty()) {
			return createGuest(request);
		}
		if (byEmail.isPresent() && byDocument.isPresent() && byEmail.get().getId().equals(byDocument.get().getId())) {
			return byEmail.get().getId();
		}
		throw new ConflictException(GUEST_CONFLICT_MESSAGE);
	}

	private UUID createGuest(PublicGuestRequest request) {
		try {
			return guestService.create(new CreateGuestRequest(
					request.firstName(),
					request.lastName(),
					request.email(),
					request.phone(),
					request.nationality(),
					request.documentType(),
					request.documentNumber(),
					null
			)).id();
		} catch (ConflictException exception) {
			// Otra petición creó el mismo huésped entre la búsqueda y el alta. El
			// mensaje original dice qué dato existe; la web pública no debe saberlo.
			throw new ConflictException(GUEST_CONFLICT_MESSAGE);
		}
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

	/** En long: adults y children enormes no pueden desbordar y pasar la capacidad. */
	private static long guestCount(int adults, int children) {
		return (long) adults + children;
	}

	private static RoomType getPublicRoomType(Optional<RoomType> roomType, UUID roomTypeId) {
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
}
