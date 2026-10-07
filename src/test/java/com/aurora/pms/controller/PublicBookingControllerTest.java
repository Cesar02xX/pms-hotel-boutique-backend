package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.aurora.pms.dto.request.PublicCreateBookingRequest;
import com.aurora.pms.dto.request.PublicGuestRequest;
import com.aurora.pms.dto.response.PublicBookingResponse;
import com.aurora.pms.exception.ConflictException;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomFeature;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.RoomStatus;
import com.aurora.pms.service.PublicBookingService;
import com.jayway.jsonpath.JsonPath;

/**
 * Contratos públicos llamados sin JWT. Cada test usa tipos de habitación propios
 * para que la disponibilidad no dependa de otros datos de la base.
 */
class PublicBookingControllerTest extends AbstractCatalogApiTest {

	private static final ZoneId HOTEL_ZONE = ZoneId.of("America/Guatemala");
	private static final long RATE_PRICE_CENTS = 45000L;
	private static final String GUEST_CONFLICT_MESSAGE =
			"Guest details conflict with an existing guest record. Please contact the hotel to complete the booking";

	@Autowired
	private PublicBookingService publicBookingService;

	private final List<String> publicConfirmationCodes = new ArrayList<>();
	private final List<String> publicGuestEmails = new ArrayList<>();

	/** Corre antes que la limpieza de la clase base: las reservas públicas apuntan a sus datos. */
	@AfterEach
	void cleanUpPublicBookings() {
		bookingRepository.findAll().stream()
				.filter(booking -> publicConfirmationCodes.contains(booking.getConfirmationCode()))
				.forEach(bookingRepository::delete);
		publicGuestEmails.forEach(email -> guestRepository.findByEmailIgnoreCase(email)
				.ifPresent(guestRepository::delete));
	}

	// --- GET /public/room-types ---

	@Test
	void roomTypesAreListedWithoutTokenWithFeaturesAndWithoutInternalFields() throws Exception {
		RoomFeature balcony = createRoomFeature("Balcony");
		UUID roomTypeId = trackCreatedRoomType(mockMvc.perform(post("/api/v1/room-types")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "P-%s", "name": "Public Suite", "description": "Volcano view",
								 "capacity": 3, "bedConfiguration": "1 king bed", "roomFeatureIds": ["%s"]}
								""".formatted(uniqueSuffix(), balcony.getId())))
				.andExpect(status().isCreated())
				.andReturn());
		RoomType inactive = createRoomType(2);
		inactive.setActive(false);
		roomTypeRepository.save(inactive);

		String body = mockMvc.perform(get("/api/v1/public/room-types"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", not(hasItem(inactive.getId().toString()))))
				.andReturn().getResponse().getContentAsString();

		List<Map<String, Object>> matches = JsonPath.read(body, "$[?(@.id == '%s')]".formatted(roomTypeId));
		assertThat(matches).singleElement().satisfies(roomType -> {
			assertThat(roomType).containsOnlyKeys(
					"id", "code", "name", "description", "capacity", "bedConfiguration", "features", "images");
			assertThat(roomType.get("images")).asList().isEmpty();
			assertThat(roomType.get("capacity")).isEqualTo(3);
			assertThat(roomType.get("features")).asList().singleElement().isEqualTo(Map.of(
					"id", balcony.getId().toString(),
					"name", balcony.getName(),
					"description", balcony.getDescription()));
		});
	}

	// --- GET /public/rates ---

	@Test
	void ratesListOnlyActiveCurrentRatesOfActiveRoomTypesWithoutInternalFields() throws Exception {
		RoomType roomType = createRoomType(2);
		Rate current = createCurrentRate(roomType);
		RoomType otherType = createRoomType(2);
		Rate inactiveRate = createRate(otherType, today().minusDays(10), today().plusDays(10));
		inactiveRate.setActive(false);
		rateRepository.save(inactiveRate);
		RoomType expiredType = createRoomType(2);
		Rate expired = createRate(expiredType, today().minusDays(30), today().minusDays(1));
		RoomType inactiveType = createRoomType(2);
		inactiveType.setActive(false);
		roomTypeRepository.save(inactiveType);
		Rate ofInactiveType = createCurrentRate(inactiveType);

		String body = mockMvc.perform(get("/api/v1/public/rates"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(current.getId().toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(inactiveRate.getId().toString()))))
				.andExpect(jsonPath("$[*].id", not(hasItem(expired.getId().toString()))))
				.andExpect(jsonPath("$[*].id", not(hasItem(ofInactiveType.getId().toString()))))
				.andReturn().getResponse().getContentAsString();

		List<Map<String, Object>> matches = JsonPath.read(body, "$[?(@.id == '%s')]".formatted(current.getId()));
		assertThat(matches).singleElement().satisfies(rate -> assertThat(rate).containsOnlyKeys(
				"id", "roomTypeId", "name", "validFrom", "validTo", "priceCents", "currency",
				"minimumNights", "refundable"));

		mockMvc.perform(get("/api/v1/public/rates").param("roomTypeId", roomType.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(current.getId().toString()));
	}

	// --- GET /public/availability ---

	@Test
	void availabilityCountsOnlyOperableRoomsAndBlockingBookings() throws Exception {
		RoomType roomType = createRoomType(2);
		createCurrentRate(roomType);
		Room first = createRoom(roomType);
		Room second = createRoom(roomType);
		createRoom(roomType);
		Room occupiedToday = createRoom(roomType, RoomStatus.occupied);
		createRoom(roomType, RoomStatus.maintenance);
		createRoom(roomType, RoomStatus.out_of_service);
		LocalDate checkIn = stayStart();
		LocalDate checkOut = checkIn.plusDays(3);

		// Bloquean (3): pending sin habitación, confirmed y checked_in con habitación.
		createStayBooking(roomType, null, checkIn, checkOut, BookingStatus.pending);
		createStayBooking(roomType, first, checkIn.plusDays(1), checkOut, BookingStatus.confirmed);
		createStayBooking(roomType, second, checkIn.minusDays(2), checkIn.plusDays(2), BookingStatus.checked_in);
		// No bloquean: estados finales y una salida el mismo día de la entrada.
		createStayBooking(roomType, null, checkIn, checkOut, BookingStatus.cancelled);
		createStayBooking(roomType, null, checkIn, checkOut, BookingStatus.checked_out);
		createStayBooking(roomType, null, checkIn, checkOut, BookingStatus.no_show);
		createStayBooking(roomType, occupiedToday, checkIn.minusDays(3), checkIn, BookingStatus.confirmed);

		String body = availability(roomType, checkIn, checkOut, "2", "0")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkIn").value(checkIn.toString()))
				.andExpect(jsonPath("$.nights").value(3))
				.andExpect(jsonPath("$.results.length()").value(1))
				.andExpect(jsonPath("$.results[0].roomTypeId").value(roomType.getId().toString()))
				.andExpect(jsonPath("$.results[0].availableRooms").value(1))
				.andExpect(jsonPath("$.results[0].totalAmountCents").value(RATE_PRICE_CENTS * 3))
				.andExpect(jsonPath("$.results[0].currency").value("GTQ"))
				.andReturn().getResponse().getContentAsString();

		Map<String, Object> result = JsonPath.read(body, "$.results[0]");
		assertThat(result).containsOnlyKeys("roomTypeId", "code", "name", "capacity", "bedConfiguration",
				"availableRooms", "rate", "totalAmountCents", "currency");
	}

	@Test
	void availabilityReturnsEmptyResultsWhenCapacityOrRateDoNotFit() throws Exception {
		RoomType withRate = createRoomType(2);
		createCurrentRate(withRate);
		createRoom(withRate);
		RoomType withoutRate = createRoomType(4);
		createRoom(withoutRate);
		RoomType longMinimum = createRoomType(2);
		Rate rate = createCurrentRate(longMinimum);
		rate.setMinimumNights(5);
		rateRepository.save(rate);
		createRoom(longMinimum);
		LocalDate checkIn = stayStart();

		availability(withRate, checkIn, checkIn.plusDays(2), "2", "1")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results.length()").value(0));
		availability(withoutRate, checkIn, checkIn.plusDays(2), "2", "0")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results.length()").value(0));
		availability(longMinimum, checkIn, checkIn.plusDays(2), "1", "0")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results.length()").value(0));
	}

	@Test
	void availabilityRejectsInvalidParameters() throws Exception {
		LocalDate checkIn = stayStart();
		RoomType inactive = createRoomType(2);
		inactive.setActive(false);
		roomTypeRepository.save(inactive);

		expectBadRequest(get("/api/v1/public/availability")
				.param("checkOut", checkIn.plusDays(1).toString()).param("adults", "1"), "Check-in date is required");
		expectBadRequest(get("/api/v1/public/availability")
				.param("checkIn", checkIn.toString()).param("checkOut", checkIn.plusDays(1).toString()),
				"Adults is required");
		mockMvc.perform(get("/api/v1/public/availability")
						.param("checkIn", "2026-13-40").param("checkOut", checkIn.toString()).param("adults", "1"))
				.andExpect(status().isBadRequest());
		expectBadRequest(availabilityRequest(today().minusDays(1), checkIn, "1"), "Check-in date cannot be in the past");
		expectBadRequest(availabilityRequest(checkIn, checkIn, "1"), "Check-in date must be before check-out date");
		expectBadRequest(availabilityRequest(checkIn, checkIn.plusDays(31), "1"), "Stay cannot exceed 30 nights");
		expectBadRequest(availabilityRequest(checkIn, checkIn.plusDays(1), "0"), "Adults must be greater than zero");
		expectBadRequest(availabilityRequest(checkIn, checkIn.plusDays(1), "1")
				.param("roomTypeId", inactive.getId().toString()), "Room type not available: " + inactive.getId());
		expectBadRequest(availabilityRequest(checkIn, checkIn.plusDays(1), "1")
				.param("roomTypeId", UUID.randomUUID().toString()), null);
	}

	// --- POST /public/bookings ---

	@Test
	void createBookingPersistsPendingBookingWithServerRateAndHidesInternalData() throws Exception {
		RoomType roomType = createRoomType(2);
		Rate rate = createCurrentRate(roomType);
		createRoom(roomType);
		LocalDate checkIn = stayStart();
		String email = newEmail();

		// rateId, roomId, status y totalAmountCents del cliente se ignoran.
		MvcResult result = mockMvc.perform(post("/api/v1/public/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomTypeId": "%s", "checkIn": "%s", "checkOut": "%s", "adults": 2, "children": 0,
								 "notes": " Late arrival ", "rateId": "%s", "roomId": "%s", "status": "confirmed",
								 "totalAmountCents": 1,
								 "guest": {"firstName": " Ana ", "lastName": "Lopez", "email": "%s",
								           "phone": "+502 5555 0000", "documentType": "passport",
								           "documentNumber": "P-%s"}}
								""".formatted(roomType.getId(), checkIn, checkIn.plusDays(2), UUID.randomUUID(),
								UUID.randomUUID(), email, uniqueSuffix())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("pending"))
				.andExpect(jsonPath("$.nights").value(2))
				.andExpect(jsonPath("$.rateName").value(rate.getName()))
				.andExpect(jsonPath("$.totalAmountCents").value(RATE_PRICE_CENTS * 2))
				.andExpect(jsonPath("$.currency").value("GTQ"))
				.andExpect(jsonPath("$.guestFirstName").value("Ana"))
				.andReturn();
		String body = result.getResponse().getContentAsString();
		String confirmationCode = trackPublicBooking(body, email);

		Map<String, Object> response = JsonPath.read(body, "$");
		assertThat(response).containsOnlyKeys("confirmationCode", "status", "roomTypeId", "roomTypeName",
				"checkIn", "checkOut", "nights", "adults", "children", "rateName", "totalAmountCents", "currency",
				"guestFirstName", "guestLastName", "guestEmail", "createdAt");
		assertThat(confirmationCode).startsWith("BKG-");

		Booking booking = findBookingByConfirmationCode(confirmationCode);
		assertThat(booking.getStatus()).isEqualTo(BookingStatus.pending);
		assertThat(booking.getRoom()).isNull();
		assertThat(booking.getRate().getId()).isEqualTo(rate.getId());
		assertThat(booking.getTotalAmountCents()).isEqualTo(RATE_PRICE_CENTS * 2);
		assertThat(booking.getNotes()).isEqualTo("Late arrival");
		Guest guest = guestRepository.findById(booking.getGuest().getId()).orElseThrow();
		assertThat(guest.getEmail()).isEqualTo(email);
		assertThat(guest.getFirstName()).isEqualTo("Ana");
	}

	@Test
	void createBookingValidatesBodyIncludingNestedGuest() throws Exception {
		RoomType roomType = createRoomType(2);

		mockMvc.perform(post("/api/v1/public/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomTypeId": "%s", "checkIn": "%s", "checkOut": "%s", "adults": 0, "children": 0,
								 "guest": {"firstName": "Ana", "lastName": "", "email": "not-an-email"}}
								""".formatted(roomType.getId(), stayStart(), stayStart().plusDays(1))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.adults").exists())
				.andExpect(jsonPath("$.errors['guest.lastName']").exists())
				.andExpect(jsonPath("$.errors['guest.email']").exists());

		mockMvc.perform(post("/api/v1/public/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomTypeId": "%s", "checkIn": "%s", "checkOut": "%s", "adults": 1, "children": 0,
								 "guest": {"firstName": "Ana", "lastName": "Lopez"}}
								""".formatted(roomType.getId(), stayStart(), stayStart().plusDays(1))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['guest.email']").value("Email is required"));

		mockMvc.perform(post("/api/v1/public/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomTypeId": "%s", "checkIn": "%s", "checkOut": "%s", "adults": 1, "children": 0}
								""".formatted(roomType.getId(), stayStart(), stayStart().plusDays(1))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.guest").value("Guest is required"));
	}

	@Test
	void createBookingRejectsCapacityDatesAndMissingRate() throws Exception {
		RoomType roomType = createRoomType(2);
		createCurrentRate(roomType);
		createRoom(roomType);
		RoomType withoutRate = createRoomType(2);
		createRoom(withoutRate);
		LocalDate checkIn = stayStart();

		expectBadRequest(bookingRequest(roomType, checkIn, checkIn.plusDays(1), 2, 1, newEmail()),
				"Guest count exceeds room type capacity");
		// adults + children no debe desbordar a un número negativo que pase la capacidad.
		expectBadRequest(bookingRequest(roomType, checkIn, checkIn.plusDays(1), Integer.MAX_VALUE, 1, newEmail()),
				"Guest count exceeds room type capacity");
		availability(roomType, checkIn, checkIn.plusDays(1), String.valueOf(Integer.MAX_VALUE), "1")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.results.length()").value(0));
		expectBadRequest(bookingRequest(roomType, today().minusDays(1), checkIn, 1, 0, newEmail()),
				"Check-in date cannot be in the past");
		expectBadRequest(bookingRequest(roomType, checkIn, checkIn.plusDays(31), 1, 0, newEmail()),
				"Stay cannot exceed 30 nights");
		expectBadRequest(bookingRequest(withoutRate, checkIn, checkIn.plusDays(1), 1, 0, newEmail()),
				"No rate available for the requested stay");
	}

	@Test
	void createBookingReturnsConflictWhenSoldOutAndDoesNotCreateGuest() throws Exception {
		RoomType roomType = createRoomType(2);
		createCurrentRate(roomType);
		createRoom(roomType);
		LocalDate checkIn = stayStart();
		createStayBooking(roomType, null, checkIn.plusDays(1), checkIn.plusDays(2), BookingStatus.pending);
		String email = newEmail();

		mockMvc.perform(bookingRequest(roomType, checkIn, checkIn.plusDays(3), 1, 0, email))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("No availability for the requested room type and dates"));

		assertThat(guestRepository.findByEmailIgnoreCase(email)).isEmpty();
	}

	@Test
	void createBookingReusesGuestWhenEmailAndDocumentMatchWithoutChangingIt() throws Exception {
		RoomType roomType = createRoomType(2);
		createCurrentRate(roomType);
		createRoom(roomType);
		Guest existing = createGuest();
		LocalDate checkIn = stayStart();

		String body = mockMvc.perform(post("/api/v1/public/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content(bookingJson(roomType, checkIn, checkIn.plusDays(1), 1, 0, "Otro", "Nombre",
								existing.getEmail().toUpperCase(), existing.getDocumentType().name(),
								existing.getDocumentNumber())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.guestFirstName").value("Otro"))
				.andExpect(jsonPath("$.guestLastName").value("Nombre"))
				.andExpect(jsonPath("$.guestEmail").value(existing.getEmail().toUpperCase()))
				.andReturn().getResponse().getContentAsString();
		String confirmationCode = trackPublicBooking(body, null);

		// La respuesta no expone los datos guardados del huésped reutilizado.
		assertThat(body).doesNotContain(existing.getLastName()).doesNotContain(existing.getPhone());

		assertThat(findBookingByConfirmationCode(confirmationCode).getGuest().getId()).isEqualTo(existing.getId());
		Guest reloaded = guestRepository.findById(existing.getId()).orElseThrow();
		assertThat(reloaded.getFirstName()).isEqualTo(existing.getFirstName());
		assertThat(reloaded.getLastName()).isEqualTo(existing.getLastName());
	}

	@Test
	void createBookingRejectsPartialGuestMatchWithGenericMessage() throws Exception {
		RoomType roomType = createRoomType(2);
		createCurrentRate(roomType);
		createRoom(roomType);
		Guest existing = createGuest();
		LocalDate checkIn = stayStart();

		// Mismo email con otro documento.
		String sameEmailBody = mockMvc.perform(post("/api/v1/public/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content(bookingJson(roomType, checkIn, checkIn.plusDays(1), 1, 0, "Ana", "Lopez",
								existing.getEmail(), "passport", "P-" + uniqueSuffix())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(GUEST_CONFLICT_MESSAGE))
				.andReturn().getResponse().getContentAsString();
		assertThat(sameEmailBody).doesNotContain(existing.getEmail());

		// Mismo documento con otro email.
		String otherEmail = newEmail();
		mockMvc.perform(post("/api/v1/public/bookings")
						.contentType(MediaType.APPLICATION_JSON)
						.content(bookingJson(roomType, checkIn, checkIn.plusDays(1), 1, 0, "Ana", "Lopez",
								otherEmail, existing.getDocumentType().name(), existing.getDocumentNumber())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(GUEST_CONFLICT_MESSAGE));

		assertThat(guestRepository.findByEmailIgnoreCase(otherEmail)).isEmpty();
		assertThat(bookingRepository.findAll()).noneMatch(booking ->
				booking.getRoomType().getId().equals(roomType.getId()));
	}

	@Test
	void concurrentPublicBookingsCannotOversellTheLastRoom() throws Exception {
		RoomType roomType = createRoomType(2);
		createCurrentRate(roomType);
		createRoom(roomType);
		LocalDate checkIn = stayStart();
		List<String> emails = List.of(newEmail(), newEmail());

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		List<Future<PublicBookingResponse>> futures = new ArrayList<>();
		try {
			for (String email : emails) {
				Callable<PublicBookingResponse> attempt = () -> {
					start.await();
					return publicBookingService.create(new PublicCreateBookingRequest(roomType.getId(), checkIn,
							checkIn.plusDays(2), 1, 0, null,
							new PublicGuestRequest("Ana", "Lopez", email, null, null, null, null)));
				};
				futures.add(executor.submit(attempt));
			}
			start.countDown();

			List<PublicBookingResponse> created = new ArrayList<>();
			List<Throwable> failures = new ArrayList<>();
			for (Future<PublicBookingResponse> future : futures) {
				try {
					created.add(future.get(30, TimeUnit.SECONDS));
				} catch (java.util.concurrent.ExecutionException exception) {
					failures.add(exception.getCause());
				}
			}
			created.forEach(response -> {
				publicConfirmationCodes.add(response.confirmationCode());
				publicGuestEmails.add(response.guestEmail());
			});

			assertThat(created).hasSize(1);
			assertThat(failures).singleElement().isInstanceOf(ConflictException.class);
			assertThat(bookingRepository.findAll()).filteredOn(booking ->
					booking.getRoomType().getId().equals(roomType.getId())).hasSize(1);
		} finally {
			executor.shutdownNow();
		}
	}

	// --- Helpers ---

	private static LocalDate today() {
		return LocalDate.now(HOTEL_ZONE);
	}

	/** Lejos de hoy para no depender de la hora en que corre el test. */
	private static LocalDate stayStart() {
		return today().plusDays(40);
	}

	private static String newEmail() {
		return "public.%s@aurora.test".formatted(uniqueSuffix());
	}

	private RoomType createRoomType(int capacity) {
		RoomType roomType = createRoomType();
		roomType.setCapacity(capacity);
		return roomTypeRepository.save(roomType);
	}

	private Room createRoom(RoomType roomType, RoomStatus status) {
		Room room = createRoom(roomType);
		room.setStatus(status);
		return roomRepository.save(room);
	}

	private Rate createCurrentRate(RoomType roomType) {
		return createRate(roomType, today(), today().plusDays(365));
	}

	private Rate createRate(RoomType roomType, LocalDate validFrom, LocalDate validTo) {
		Rate rate = createRate(roomType);
		rate.setName("Public Rate " + uniqueSuffix());
		rate.setValidFrom(validFrom);
		rate.setValidTo(validTo);
		rate.setPriceCents(RATE_PRICE_CENTS);
		return rateRepository.save(rate);
	}

	private Booking createStayBooking(RoomType roomType, Room room, LocalDate checkIn, LocalDate checkOut,
			BookingStatus status) {
		Booking booking = createBooking(createGuest(), roomType, room, null);
		booking.setCheckIn(checkIn);
		booking.setCheckOut(checkOut);
		booking.setStatus(status);
		return bookingRepository.save(booking);
	}

	private Booking findBookingByConfirmationCode(String confirmationCode) {
		return bookingRepository.findAll().stream()
				.filter(booking -> booking.getConfirmationCode().equals(confirmationCode))
				.findFirst()
				.orElseThrow();
	}

	private String trackPublicBooking(String body, String newGuestEmail) {
		String confirmationCode = JsonPath.read(body, "$.confirmationCode");
		publicConfirmationCodes.add(confirmationCode);
		if (newGuestEmail != null) {
			publicGuestEmails.add(newGuestEmail);
		}
		return confirmationCode;
	}

	private ResultActions availability(RoomType roomType, LocalDate checkIn, LocalDate checkOut, String adults,
			String children) throws Exception {
		return mockMvc.perform(availabilityRequest(checkIn, checkOut, adults)
				.param("children", children)
				.param("roomTypeId", roomType.getId().toString()));
	}

	private static MockHttpServletRequestBuilder availabilityRequest(
			LocalDate checkIn, LocalDate checkOut, String adults) {
		return get("/api/v1/public/availability")
				.param("checkIn", checkIn.toString())
				.param("checkOut", checkOut.toString())
				.param("adults", adults);
	}

	private MockHttpServletRequestBuilder bookingRequest(
			RoomType roomType, LocalDate checkIn, LocalDate checkOut, int adults, int children, String email) {
		return post("/api/v1/public/bookings")
				.contentType(MediaType.APPLICATION_JSON)
				.content(bookingJson(roomType, checkIn, checkOut, adults, children, "Ana", "Lopez", email, null, null));
	}

	private static String bookingJson(RoomType roomType, LocalDate checkIn, LocalDate checkOut, int adults,
			int children, String firstName, String lastName, String email, String documentType,
			String documentNumber) {
		String document = documentType == null ? ""
				: ", \"documentType\": \"%s\", \"documentNumber\": \"%s\"".formatted(documentType, documentNumber);
		return """
				{"roomTypeId": "%s", "checkIn": "%s", "checkOut": "%s", "adults": %d, "children": %d,
				 "guest": {"firstName": "%s", "lastName": "%s", "email": "%s"%s}}
				""".formatted(roomType.getId(), checkIn, checkOut, adults, children, firstName, lastName, email,
				document);
	}

	private void expectBadRequest(
			MockHttpServletRequestBuilder request,
			String message
	) throws Exception {
		ResultActions result = mockMvc.perform(request)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
		if (message != null) {
			result.andExpect(jsonPath("$.message").value(message));
		}
	}
}
