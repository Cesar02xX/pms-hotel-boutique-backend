package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.aurora.pms.dto.request.CreateGuestBookingRequest;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.security.GuestPrincipal;
import com.aurora.pms.service.GuestAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;

class GuestBookingControllerTest extends AbstractCatalogApiTest {

	private static final String BASE_PATH = "/api/v1/guest/bookings";
	private static final ZoneId HOTEL_ZONE = ZoneId.of("America/Guatemala");

	@Autowired
	private GuestAccessService guestAccessService;

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
	private final List<UUID> createdBookingIds = new ArrayList<>();

	@AfterEach
	void cleanUpBookings() {
		for (UUID bookingId : createdBookingIds) {
			bookingRepository.findById(bookingId).ifPresent(bookingRepository::delete);
		}
		createdBookingIds.clear();
	}

	@Test
	void listBookingsReturnsOnlyBookingsForAuthenticatedGuest() throws Exception {
		Guest guestA = createGuest();
		Guest guestB = createGuest();
		RoomType roomType = createRoomType(2);
		Room room = createRoom(roomType);
		Rate rate = createCurrentRate(roomType);

		Booking bookingA1 = createBooking(guestA, roomType, room, rate);
		bookingA1.setStatus(BookingStatus.checked_in);
		bookingRepository.save(bookingA1);
		createdBookingIds.add(bookingA1.getId());

		Booking bookingA2 = createBooking(guestA, roomType, null, rate);
		bookingA2.setCheckIn(today().plusDays(10));
		bookingA2.setCheckOut(today().plusDays(13));
		bookingA2.setStatus(BookingStatus.confirmed);
		bookingRepository.save(bookingA2);
		createdBookingIds.add(bookingA2.getId());

		Booking bookingB = createBooking(guestB, roomType, null, rate);
		bookingB.setCheckIn(today().plusDays(20));
		bookingB.setCheckOut(today().plusDays(22));
		bookingRepository.save(bookingB);
		createdBookingIds.add(bookingB.getId());

		mockMvc.perform(get(BASE_PATH).with(guestOf(bookingA1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()", is(2)))
				.andExpect(jsonPath("$[*].id", hasItem(bookingA1.getId().toString())))
				.andExpect(jsonPath("$[*].id", hasItem(bookingA2.getId().toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(bookingB.getId().toString()))));
	}

	@Test
	void listBookingsEmptyWhenGuestHasNoOtherBookings() throws Exception {
		Guest guestA = createGuest();
		RoomType roomType = createRoomType(2);
		Booking bookingA = createBooking(guestA, roomType, null, createCurrentRate(roomType));
		bookingA.setStatus(BookingStatus.checked_in);
		bookingRepository.save(bookingA);
		createdBookingIds.add(bookingA.getId());

		// A different guest without bookings
		Guest guestEmpty = createGuest();
		GuestPrincipal emptyPrincipal = new GuestPrincipal(bookingA.getId(), guestEmpty.getId(), bookingA.getGuestLinkCode());

		mockMvc.perform(get(BASE_PATH).with(user(emptyPrincipal)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()", is(0)));
	}

	@Test
	void getBookingByIdReturnsOwnBooking() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType(2);
		Booking booking = createBooking(guest, roomType, null, createCurrentRate(roomType));
		booking.setStatus(BookingStatus.checked_in);
		bookingRepository.save(booking);
		createdBookingIds.add(booking.getId());

		mockMvc.perform(get(BASE_PATH + "/" + booking.getId()).with(guestOf(booking)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(booking.getId().toString())))
				.andExpect(jsonPath("$.guestId", is(guest.getId().toString())))
				.andExpect(jsonPath("$.status", is(booking.getStatus().name())));
	}

	@Test
	void getBookingByIdRejectsAccessToAnotherGuestsBooking() throws Exception {
		Guest guestA = createGuest();
		Guest guestB = createGuest();
		RoomType roomType = createRoomType(2);
		Booking bookingA = createBooking(guestA, roomType, null, createCurrentRate(roomType));
		bookingA.setStatus(BookingStatus.checked_in);
		bookingRepository.save(bookingA);
		createdBookingIds.add(bookingA.getId());

		Booking bookingB = createBooking(guestB, roomType, null, createCurrentRate(roomType));
		bookingRepository.save(bookingB);
		createdBookingIds.add(bookingB.getId());

		// Guest A trying to access Guest B's booking
		mockMvc.perform(get(BASE_PATH + "/" + bookingB.getId()).with(guestOf(bookingA)))
				.andExpect(status().isNotFound());
	}

	@Test
	void createBookingSuccessfullyCreatesAndAssociatesToAuthenticatedGuest() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType(2);
		createRoom(roomType);
		Rate rate = createCurrentRate(roomType);

		Booking existing = createBooking(guest, roomType, null, rate);
		existing.setStatus(BookingStatus.checked_in);
		bookingRepository.save(existing);
		createdBookingIds.add(existing.getId());

		CreateGuestBookingRequest request = new CreateGuestBookingRequest(
				roomType.getId(),
				today().plusDays(5),
				today().plusDays(7),
				2,
				0,
				"Second reservation from guest portal"
		);

		MvcResult result = mockMvc.perform(post(BASE_PATH)
						.with(guestOf(existing))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.guestId", is(guest.getId().toString())))
				.andExpect(jsonPath("$.roomTypeId", is(roomType.getId().toString())))
				.andExpect(jsonPath("$.status", is(BookingStatus.pending.name())))
				.andExpect(jsonPath("$.adults", is(2)))
				.andExpect(jsonPath("$.children", is(0)))
				.andExpect(jsonPath("$.notes", is("Second reservation from guest portal")))
				.andReturn();

		String newBookingId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		createdBookingIds.add(UUID.fromString(newBookingId));

		// Verify it now appears in the list
		mockMvc.perform(get(BASE_PATH).with(guestOf(existing)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(newBookingId)));
	}

	@Test
	void createBookingFailsWithInvalidDates() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType(2);
		Booking existing = createBooking(guest, roomType, null, createCurrentRate(roomType));
		existing.setStatus(BookingStatus.checked_in);
		bookingRepository.save(existing);
		createdBookingIds.add(existing.getId());

		// Past check-in
		CreateGuestBookingRequest pastRequest = new CreateGuestBookingRequest(
				roomType.getId(),
				today().minusDays(1),
				today().plusDays(2),
				2,
				0,
				null
		);
		mockMvc.perform(post(BASE_PATH)
						.with(guestOf(existing))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(pastRequest)))
				.andExpect(status().isBadRequest());

		// Check-out before check-in
		CreateGuestBookingRequest checkoutBeforeCheckin = new CreateGuestBookingRequest(
				roomType.getId(),
				today().plusDays(5),
				today().plusDays(3),
				2,
				0,
				null
		);
		mockMvc.perform(post(BASE_PATH)
						.with(guestOf(existing))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(checkoutBeforeCheckin)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createBookingFailsWhenOverCapacity() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType(2); // capacity 2
		Booking existing = createBooking(guest, roomType, null, createCurrentRate(roomType));
		existing.setStatus(BookingStatus.checked_in);
		bookingRepository.save(existing);
		createdBookingIds.add(existing.getId());

		CreateGuestBookingRequest overCapacityRequest = new CreateGuestBookingRequest(
				roomType.getId(),
				today().plusDays(5),
				today().plusDays(7),
				3,
				0,
				null
		);
		mockMvc.perform(post(BASE_PATH)
						.with(guestOf(existing))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(overCapacityRequest)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createBookingReturnsConflictWhenNoAvailability() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType(2);
		createRoom(roomType); // Only 1 room
		Rate rate = createCurrentRate(roomType);

		// An existing booking already occupying the room
		Booking existingBooking = createBooking(createGuest(), roomType, null, rate);
		existingBooking.setCheckIn(today().plusDays(5));
		existingBooking.setCheckOut(today().plusDays(8));
		existingBooking.setStatus(BookingStatus.confirmed);
		bookingRepository.save(existingBooking);
		createdBookingIds.add(existingBooking.getId());

		Booking guestSessionBooking = createBooking(guest, roomType, null, rate);
		guestSessionBooking.setStatus(BookingStatus.checked_in);
		bookingRepository.save(guestSessionBooking);
		createdBookingIds.add(guestSessionBooking.getId());

		CreateGuestBookingRequest overlappingRequest = new CreateGuestBookingRequest(
				roomType.getId(),
				today().plusDays(6),
				today().plusDays(7),
				2,
				0,
				null
		);

		mockMvc.perform(post(BASE_PATH)
						.with(guestOf(guestSessionBooking))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(overlappingRequest)))
				.andExpect(status().isConflict());
	}

	@Test
	void concurrentBookingCreationPreventsDoubleBooking() throws Exception {
		Guest guest1 = createGuest();
		Guest guest2 = createGuest();
		RoomType roomType = createRoomType(2);
		createRoom(roomType); // Exactly 1 operable room
		Rate rate = createCurrentRate(roomType);

		Booking b1 = createBooking(guest1, roomType, null, rate);
		b1.setStatus(BookingStatus.checked_in);
		bookingRepository.save(b1);
		createdBookingIds.add(b1.getId());

		Booking b2 = createBooking(guest2, roomType, null, rate);
		b2.setStatus(BookingStatus.checked_in);
		bookingRepository.save(b2);
		createdBookingIds.add(b2.getId());

		LocalDate checkIn = today().plusDays(10);
		LocalDate checkOut = today().plusDays(12);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch readyLatch = new CountDownLatch(2);
		CountDownLatch startLatch = new CountDownLatch(1);

		Callable<Integer> task1 = () -> {
			readyLatch.countDown();
			startLatch.await();
			try {
				var res = guestAccessService.createBooking(guest1.getId(),
						new CreateGuestBookingRequest(roomType.getId(), checkIn, checkOut, 1, 0, null));
				synchronized (createdBookingIds) {
					createdBookingIds.add(res.id());
				}
				return 201;
			} catch (com.aurora.pms.exception.ConflictException e) {
				return 409;
			}
		};

		Callable<Integer> task2 = () -> {
			readyLatch.countDown();
			startLatch.await();
			try {
				var res = guestAccessService.createBooking(guest2.getId(),
						new CreateGuestBookingRequest(roomType.getId(), checkIn, checkOut, 1, 0, null));
				synchronized (createdBookingIds) {
					createdBookingIds.add(res.id());
				}
				return 201;
			} catch (com.aurora.pms.exception.ConflictException e) {
				return 409;
			}
		};


		Future<Integer> f1 = executor.submit(task1);
		Future<Integer> f2 = executor.submit(task2);

		assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
		startLatch.countDown();

		int status1 = f1.get(10, TimeUnit.SECONDS);
		int status2 = f2.get(10, TimeUnit.SECONDS);
		executor.shutdown();

		List<Integer> statuses = List.of(status1, status2);
		assertThat(statuses).containsExactlyInAnyOrder(201, 409);
	}

	@Test
	void endpointsRequireGuestIdentity() throws Exception {
		mockMvc.perform(get(BASE_PATH)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(BASE_PATH + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
		mockMvc.perform(post(BASE_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized());

		// Staff token should be forbidden on guest endpoints
		mockMvc.perform(get(BASE_PATH).with(staffUser())).andExpect(status().isForbidden());
	}

	private RequestPostProcessor guestOf(Booking booking) {
		return user(new GuestPrincipal(booking.getId(), booking.getGuest().getId(), booking.getGuestLinkCode()));
	}

	private LocalDate today() {
		return LocalDate.now(HOTEL_ZONE);
	}

	private RoomType createRoomType(int capacity) {
		RoomType roomType = createRoomType();
		roomType.setCapacity(capacity);
		return roomTypeRepository.save(roomType);
	}

	private Rate createCurrentRate(RoomType roomType) {
		return createRate(roomType, today().minusDays(1), today().plusDays(30));
	}

	private Rate createRate(RoomType roomType, LocalDate validFrom, LocalDate validTo) {
		Rate rate = createRate(roomType);
		rate.setValidFrom(validFrom);
		rate.setValidTo(validTo);
		return rateRepository.save(rate);
	}
}

