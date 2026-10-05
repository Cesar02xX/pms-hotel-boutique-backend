package com.aurora.pms.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aurora.pms.dto.request.CreateBookingRequest;
import com.aurora.pms.dto.request.CreateGuestRequest;
import com.aurora.pms.dto.request.PublicCreateBookingRequest;
import com.aurora.pms.dto.request.PublicGuestRequest;
import com.aurora.pms.dto.response.BookingResponse;
import com.aurora.pms.dto.response.GuestResponse;
import com.aurora.pms.dto.response.PublicAvailabilityResponse;
import com.aurora.pms.dto.response.PublicBookingResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.mapper.PublicBookingMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.DocumentType;
import com.aurora.pms.model.enums.RoomStatus;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.GuestRepository;
import com.aurora.pms.repository.RateRepository;
import com.aurora.pms.repository.RoomFeatureRepository;
import com.aurora.pms.repository.RoomRepository;
import com.aurora.pms.repository.RoomTypeFeatureRepository;
import com.aurora.pms.repository.RoomTypeRepository;
import com.aurora.pms.service.BookingService;
import com.aurora.pms.service.GuestService;

@ExtendWith(MockitoExtension.class)
class PublicBookingServiceImplTest {

	/** 12:00 en Guatemala del 5 de octubre de 2026. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZoneOffset.UTC);
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
	private static final LocalDate CHECK_IN = TODAY.plusDays(10);
	private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(4);

	@Mock
	private RoomTypeRepository roomTypeRepository;

	@Mock
	private RoomTypeFeatureRepository roomTypeFeatureRepository;

	@Mock
	private RoomFeatureRepository roomFeatureRepository;

	@Mock
	private RateRepository rateRepository;

	@Mock
	private GuestRepository guestRepository;

	@Mock
	private RoomRepository roomRepository;

	@Mock
	private BookingRepository bookingRepository;

	@Mock
	private GuestService guestService;

	@Mock
	private BookingService bookingService;

	private PublicBookingServiceImpl service;
	private RoomType roomType;
	private Rate rate;

	@BeforeEach
	void setUp() {
		RoomTypeAvailability availability = new RoomTypeAvailability(roomRepository, bookingRepository, rateRepository);
		service = new PublicBookingServiceImpl(
				roomTypeRepository,
				roomTypeFeatureRepository,
				roomFeatureRepository,
				rateRepository,
				guestRepository,
				availability,
				guestService,
				bookingService,
				new PublicBookingMapper(),
				CLOCK,
				"America/Guatemala"
		);
		roomType = roomType(2);
		rate = rate(roomType, 50000L, 1);
	}

	// --- Disponibilidad ---

	@Test
	void availabilityCountsBusiestNightInsteadOfAllOverlappingBookings() {
		// Dos reservas consecutivas que no se cruzan entre sí ocupan una sola habitación.
		stubCatalog(List.of(room(roomType, RoomStatus.available), room(roomType, RoomStatus.available)),
				List.of(booking(roomType, CHECK_IN, CHECK_IN.plusDays(2)),
						booking(roomType, CHECK_IN.plusDays(2), CHECK_OUT)));

		PublicAvailabilityResponse response = service.findAvailability(CHECK_IN, CHECK_OUT, 2, null, null);

		assertThat(response.nights()).isEqualTo(4);
		assertThat(response.children()).isZero();
		assertThat(response.results()).singleElement().satisfies(result -> {
			assertThat(result.roomTypeId()).isEqualTo(roomType.getId());
			assertThat(result.availableRooms()).isEqualTo(1);
			assertThat(result.rate().id()).isEqualTo(rate.getId());
			assertThat(result.totalAmountCents()).isEqualTo(200000L);
			assertThat(result.currency()).isEqualTo("GTQ");
		});
	}

	@Test
	void availabilityExcludesRoomTypeWhenEveryRoomIsTakenOnSomeNight() {
		stubCatalog(List.of(room(roomType, RoomStatus.available)),
				List.of(booking(roomType, CHECK_IN.plusDays(1), CHECK_IN.plusDays(2))));

		assertThat(service.findAvailability(CHECK_IN, CHECK_OUT, 1, 0, null).results()).isEmpty();
	}

	@Test
	void availabilityAllowsCheckInOnPreviousCheckOutDay() {
		stubCatalog(List.of(room(roomType, RoomStatus.available)),
				List.of(booking(roomType, CHECK_IN.minusDays(3), CHECK_IN)));

		assertThat(service.findAvailability(CHECK_IN, CHECK_OUT, 1, 0, null).results())
				.singleElement()
				.satisfies(result -> assertThat(result.availableRooms()).isEqualTo(1));
	}

	@Test
	void availabilityExcludesRoomTypesWithoutCapacityForTheGuests() {
		when(roomTypeRepository.findByActiveTrueOrderByName()).thenReturn(List.of(roomType));

		PublicAvailabilityResponse response = service.findAvailability(CHECK_IN, CHECK_OUT, 2, 1, null);

		assertThat(response.results()).isEmpty();
		verifyNoInteractions(roomRepository, bookingRepository);
	}

	@Test
	void availabilityExcludesRoomTypesWithoutApplicableRate() {
		when(roomTypeRepository.findByActiveTrueOrderByName()).thenReturn(List.of(roomType));
		when(rateRepository.findActiveCoveringStay(anyCollection(), eq(CHECK_IN), eq(CHECK_OUT.minusDays(1))))
				.thenReturn(List.of());
		when(roomRepository.findByRoomTypeIdInAndStatusNotIn(anyCollection(), anyCollection()))
				.thenReturn(List.of(room(roomType, RoomStatus.available)));
		when(bookingRepository.findActiveOverlappingByRoomTypes(anyCollection(), anyCollection(), eq(CHECK_IN),
				eq(CHECK_OUT))).thenReturn(List.of());

		assertThat(service.findAvailability(CHECK_IN, CHECK_OUT, 1, 0, null).results()).isEmpty();
	}

	@Test
	void availabilityExcludesRoomTypesWhenStayIsShorterThanRateMinimumNights() {
		rate.setMinimumNights(5);
		stubCatalog(List.of(room(roomType, RoomStatus.available)), List.of());

		assertThat(service.findAvailability(CHECK_IN, CHECK_OUT, 1, 0, null).results()).isEmpty();
	}

	@Test
	void availabilityRejectsInactiveOrUnknownRoomTypeFilter() {
		roomType.setActive(false);
		when(roomTypeRepository.findById(roomType.getId())).thenReturn(Optional.of(roomType));

		assertThatThrownBy(() -> service.findAvailability(CHECK_IN, CHECK_OUT, 1, 0, roomType.getId()))
				.isInstanceOf(BadRequestException.class)
				.hasMessageStartingWith("Room type not available");
	}

	@Test
	void availabilityValidatesRequiredParametersAndStay() {
		assertBadRequest(() -> service.findAvailability(null, CHECK_OUT, 1, 0, null), "Check-in date is required");
		assertBadRequest(() -> service.findAvailability(CHECK_IN, null, 1, 0, null), "Check-out date is required");
		assertBadRequest(() -> service.findAvailability(CHECK_IN, CHECK_OUT, null, 0, null), "Adults is required");
		assertBadRequest(() -> service.findAvailability(CHECK_IN, CHECK_OUT, 0, 0, null),
				"Adults must be greater than zero");
		assertBadRequest(() -> service.findAvailability(CHECK_IN, CHECK_OUT, 1, -1, null),
				"Children must not be negative");
		assertBadRequest(() -> service.findAvailability(CHECK_IN, CHECK_IN, 1, 0, null),
				"Check-in date must be before check-out date");
		assertBadRequest(() -> service.findAvailability(TODAY.minusDays(1), CHECK_OUT, 1, 0, null),
				"Check-in date cannot be in the past");
		assertBadRequest(() -> service.findAvailability(CHECK_IN, CHECK_IN.plusDays(31), 1, 0, null),
				"Stay cannot exceed 30 nights");
		verifyNoInteractions(roomTypeRepository, roomRepository, bookingRepository, rateRepository);
	}

	@Test
	void availabilityAcceptsTodayAndExactlyThirtyNights() {
		when(roomTypeRepository.findByActiveTrueOrderByName()).thenReturn(List.of());

		assertThat(service.findAvailability(TODAY, TODAY.plusDays(30), 1, 0, null).nights()).isEqualTo(30);
	}

	// --- Reserva pública ---

	@Test
	void createBuildsPendingBookingWithServerRateAndNewGuest() {
		stubBookableRoomType(List.of());
		PublicCreateBookingRequest request = request(guestRequest("ana@aurora.test", DocumentType.passport, "P-1"));
		when(guestRepository.findByEmailIgnoreCase("ana@aurora.test")).thenReturn(Optional.empty());
		when(guestRepository.findByDocumentTypeAndDocumentNumber(DocumentType.passport, "P-1"))
				.thenReturn(Optional.empty());
		GuestResponse createdGuest = guestResponse(UUID.randomUUID(), "ana@aurora.test");
		when(guestService.create(any(CreateGuestRequest.class))).thenReturn(createdGuest);
		when(bookingService.create(any(CreateBookingRequest.class))).thenAnswer(invocation ->
				bookingResponse(invocation.getArgument(0)));

		PublicBookingResponse response = service.create(request);

		ArgumentCaptor<CreateBookingRequest> captor = ArgumentCaptor.forClass(CreateBookingRequest.class);
		verify(bookingService).create(captor.capture());
		CreateBookingRequest sent = captor.getValue();
		assertThat(sent.guestId()).isEqualTo(createdGuest.id());
		assertThat(sent.roomTypeId()).isEqualTo(roomType.getId());
		assertThat(sent.roomId()).isNull();
		assertThat(sent.rateId()).isEqualTo(rate.getId());
		assertThat(sent.notes()).isEqualTo("Late arrival");

		assertThat(response.status()).isEqualTo(BookingStatus.pending);
		assertThat(response.confirmationCode()).isEqualTo("BKG-TEST0001");
		assertThat(response.nights()).isEqualTo(4);
		assertThat(response.rateName()).isEqualTo(rate.getName());
		assertThat(response.roomTypeName()).isEqualTo(roomType.getName());
		assertThat(response.guestEmail()).isEqualTo("ana@aurora.test");
	}

	@Test
	void createReusesGuestWhenEmailAndDocumentMatchTheSameGuest() {
		stubBookableRoomType(List.of());
		Guest existing = guest("ana@aurora.test", DocumentType.passport, "P-1");
		when(guestRepository.findByEmailIgnoreCase("ANA@aurora.test")).thenReturn(Optional.of(existing));
		when(guestRepository.findByDocumentTypeAndDocumentNumber(DocumentType.passport, "P-1"))
				.thenReturn(Optional.of(existing));
		when(bookingService.create(any(CreateBookingRequest.class))).thenAnswer(invocation ->
				bookingResponse(invocation.getArgument(0)));

		PublicBookingResponse response = service.create(
				request(guestRequest("  ANA@aurora.test ", DocumentType.passport, " P-1 ")));

		verify(guestService, never()).create(any());
		ArgumentCaptor<CreateBookingRequest> captor = ArgumentCaptor.forClass(CreateBookingRequest.class);
		verify(bookingService).create(captor.capture());
		assertThat(captor.getValue().guestId()).isEqualTo(existing.getId());
		// La respuesta repite lo enviado; no expone lo guardado ("Stored Guest").
		assertThat(response.guestFirstName()).isEqualTo("Ana");
		assertThat(response.guestLastName()).isEqualTo("Lopez");
		assertThat(response.guestEmail()).isEqualTo("ANA@aurora.test");
	}

	@Test
	void availabilityDoesNotOverflowGuestCountIntoAPassingCapacity() {
		when(roomTypeRepository.findByActiveTrueOrderByName()).thenReturn(List.of(roomType));

		assertThat(service.findAvailability(CHECK_IN, CHECK_OUT, Integer.MAX_VALUE, 1, null).results()).isEmpty();
		verifyNoInteractions(roomRepository, bookingRepository);
	}

	@Test
	void createDoesNotOverflowGuestCountIntoAPassingCapacity() {
		when(roomTypeRepository.findByIdForUpdate(roomType.getId())).thenReturn(Optional.of(roomType));

		assertBadRequest(() -> service.create(new PublicCreateBookingRequest(roomType.getId(), CHECK_IN, CHECK_OUT,
				Integer.MAX_VALUE, 1, null, guestRequest("ana@aurora.test", null, null))),
				"Guest count exceeds room type capacity");
		verifyNoInteractions(guestService, bookingService);
	}

	@Test
	void createRejectsPartialGuestMatchWithGenericConflict() {
		stubBookableRoomType(List.of());
		when(guestRepository.findByEmailIgnoreCase("ana@aurora.test"))
				.thenReturn(Optional.of(guest("ana@aurora.test", DocumentType.passport, "P-OTHER")));
		when(guestRepository.findByDocumentTypeAndDocumentNumber(DocumentType.passport, "P-1"))
				.thenReturn(Optional.empty());

		assertGenericGuestConflict(request(guestRequest("ana@aurora.test", DocumentType.passport, "P-1")));
	}

	@Test
	void createRejectsEmailOnlyMatchWhenNoDocumentIsSent() {
		stubBookableRoomType(List.of());
		when(guestRepository.findByEmailIgnoreCase("ana@aurora.test"))
				.thenReturn(Optional.of(guest("ana@aurora.test", null, null)));

		assertGenericGuestConflict(request(guestRequest("ana@aurora.test", null, null)));
	}

	@Test
	void createRejectsEmailAndDocumentThatBelongToDifferentGuests() {
		stubBookableRoomType(List.of());
		when(guestRepository.findByEmailIgnoreCase("ana@aurora.test"))
				.thenReturn(Optional.of(guest("ana@aurora.test", null, null)));
		when(guestRepository.findByDocumentTypeAndDocumentNumber(DocumentType.passport, "P-1"))
				.thenReturn(Optional.of(guest("other@aurora.test", DocumentType.passport, "P-1")));

		assertGenericGuestConflict(request(guestRequest("ana@aurora.test", DocumentType.passport, "P-1")));
	}

	@Test
	void createHidesWhichGuestFieldCollidedWhenGuestServiceReportsDuplicate() {
		stubBookableRoomType(List.of());
		when(guestRepository.findByEmailIgnoreCase("ana@aurora.test")).thenReturn(Optional.empty());
		when(guestService.create(any(CreateGuestRequest.class)))
				.thenThrow(new ConflictException("Guest email already exists: ana@aurora.test"));

		assertGenericGuestConflict(request(guestRequest("ana@aurora.test", null, null)));
	}

	@Test
	void createRejectsWhenRoomTypeHasNoAvailabilityBeforeTouchingGuests() {
		stubBookableRoomType(List.of(booking(roomType, CHECK_IN, CHECK_OUT)));

		assertThatThrownBy(() -> service.create(request(guestRequest("ana@aurora.test", null, null))))
				.isInstanceOf(ConflictException.class)
				.hasMessage(PublicBookingServiceImpl.NO_AVAILABILITY_MESSAGE);
		verifyNoInteractions(guestRepository, guestService, bookingService);
	}

	@Test
	void createRejectsInactiveRoomTypeCapacityAndMissingRate() {
		PublicGuestRequest guest = guestRequest("ana@aurora.test", null, null);

		roomType.setActive(false);
		when(roomTypeRepository.findByIdForUpdate(roomType.getId())).thenReturn(Optional.of(roomType));
		assertBadRequest(() -> service.create(request(guest)), "Room type not available: " + roomType.getId());

		roomType.setActive(true);
		assertBadRequest(() -> service.create(new PublicCreateBookingRequest(
				roomType.getId(), CHECK_IN, CHECK_OUT, 2, 1, null, guest)), "Guest count exceeds room type capacity");

		when(rateRepository.findActiveCoveringStay(anyCollection(), eq(CHECK_IN), eq(CHECK_OUT.minusDays(1))))
				.thenReturn(List.of());
		assertBadRequest(() -> service.create(request(guest)), "No rate available for the requested stay");

		verifyNoInteractions(guestService, bookingService);
	}

	@Test
	void createLocksRoomTypeBeforeCheckingAvailability() {
		when(roomTypeRepository.findByIdForUpdate(roomType.getId())).thenReturn(Optional.empty());

		assertBadRequest(() -> service.create(request(guestRequest("ana@aurora.test", null, null))),
				"Room type not available: " + roomType.getId());
		verify(roomTypeRepository, never()).findById(any());
	}

	// --- Helpers ---

	private void stubCatalog(List<Room> rooms, List<Booking> bookings) {
		when(roomTypeRepository.findByActiveTrueOrderByName()).thenReturn(List.of(roomType));
		stubAvailability(rooms, bookings);
	}

	private void stubBookableRoomType(List<Booking> bookings) {
		when(roomTypeRepository.findByIdForUpdate(roomType.getId())).thenReturn(Optional.of(roomType));
		stubAvailability(List.of(room(roomType, RoomStatus.available)), bookings);
	}

	private void stubAvailability(List<Room> rooms, List<Booking> bookings) {
		when(rateRepository.findActiveCoveringStay(anyCollection(), eq(CHECK_IN), eq(CHECK_OUT.minusDays(1))))
				.thenReturn(List.of(rate));
		when(roomRepository.findByRoomTypeIdInAndStatusNotIn(anyCollection(),
				eq(RoomTypeAvailability.NON_OPERABLE_ROOM_STATUSES))).thenReturn(rooms);
		when(bookingRepository.findActiveOverlappingByRoomTypes(anyCollection(),
				eq(RoomTypeAvailability.BLOCKING_STATUSES), eq(CHECK_IN), eq(CHECK_OUT))).thenReturn(bookings);
	}

	private void assertGenericGuestConflict(PublicCreateBookingRequest request) {
		assertThatThrownBy(() -> service.create(request))
				.isInstanceOf(ConflictException.class)
				.hasMessage(PublicBookingServiceImpl.GUEST_CONFLICT_MESSAGE)
				.message().doesNotContain("ana@aurora.test").doesNotContainIgnoringCase("email")
				.doesNotContainIgnoringCase("document");
		verify(bookingService, never()).create(any());
	}

	private static void assertBadRequest(Runnable call, String message) {
		assertThatThrownBy(call::run).isInstanceOf(BadRequestException.class).hasMessage(message);
	}

	private PublicCreateBookingRequest request(PublicGuestRequest guest) {
		return new PublicCreateBookingRequest(roomType.getId(), CHECK_IN, CHECK_OUT, 2, 0, "  Late arrival ", guest);
	}

	private static PublicGuestRequest guestRequest(String email, DocumentType documentType, String documentNumber) {
		return new PublicGuestRequest("Ana", "Lopez", email, null, null, documentType, documentNumber);
	}

	private static RoomType roomType(int capacity) {
		RoomType roomType = new RoomType();
		roomType.setId(UUID.randomUUID());
		roomType.setCode("DBL");
		roomType.setName("Doble");
		roomType.setCapacity(capacity);
		roomType.setBedConfiguration("1 queen bed");
		roomType.setActive(true);
		return roomType;
	}

	private static Rate rate(RoomType roomType, long priceCents, int minimumNights) {
		Rate rate = new Rate();
		rate.setId(UUID.randomUUID());
		rate.setRoomType(roomType);
		rate.setName("Flexible");
		rate.setValidFrom(TODAY);
		rate.setPriceCents(priceCents);
		rate.setMinimumNights(minimumNights);
		return rate;
	}

	private static Room room(RoomType roomType, RoomStatus status) {
		Room room = new Room();
		room.setId(UUID.randomUUID());
		room.setRoomType(roomType);
		room.setStatus(status);
		return room;
	}

	private static Booking booking(RoomType roomType, LocalDate checkIn, LocalDate checkOut) {
		Booking booking = new Booking();
		booking.setId(UUID.randomUUID());
		booking.setRoomType(roomType);
		booking.setCheckIn(checkIn);
		booking.setCheckOut(checkOut);
		booking.setStatus(BookingStatus.pending);
		return booking;
	}

	private static Guest guest(String email, DocumentType documentType, String documentNumber) {
		Guest guest = new Guest();
		guest.setId(UUID.randomUUID());
		guest.setFirstName("Stored");
		guest.setLastName("Guest");
		guest.setEmail(email);
		guest.setDocumentType(documentType);
		guest.setDocumentNumber(documentNumber);
		return guest;
	}

	private static GuestResponse guestResponse(UUID id, String email) {
		return new GuestResponse(id, "Ana", "Lopez", email, null, null, null, null, null, null, null);
	}

	private BookingResponse bookingResponse(CreateBookingRequest request) {
		OffsetDateTime now = OffsetDateTime.now(CLOCK);
		return new BookingResponse(UUID.randomUUID(), "BKG-TEST0001", "GL-TEST0001", request.guestId(), null,
				request.roomTypeId(), request.rateId(), request.checkIn(), request.checkOut(), BookingStatus.pending,
				request.adults(), request.children(), rate.getPriceCents() * 4, "GTQ", request.notes(), now, now);
	}
}
