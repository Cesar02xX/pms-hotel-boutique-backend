package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.GuestAccount;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.GuestAccountStatus;
import com.aurora.pms.model.enums.GuestType;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.RoomStatus;
import com.aurora.pms.repository.GuestAccountRepository;

@TestPropertySource(properties = "pms.hotel.zone-id=America/Guatemala")
class BookingCheckInControllerTest extends AbstractCatalogApiTest {

	private static final ZoneId HOTEL_ZONE = ZoneId.of("America/Guatemala");
	private static final LocalDate HOTEL_TODAY = LocalDate.of(2026, 3, 10);

	@Autowired
	private GuestAccountRepository guestAccountRepository;

	private final List<UUID> checkoutBookingIds = new ArrayList<>();

	@AfterEach
	void cleanUpCheckoutAccounts() {
		checkoutBookingIds.forEach(bookingId ->
				guestAccountRepository.findByBookingId(bookingId).ifPresent(guestAccountRepository::delete));
	}

	@Test
	void checkInValidBookingReturnsOkAndUpdatesBookingAndRoom() throws Exception {
		Booking booking = createCheckInReadyBooking(3, 2, 1);
		createBookingCompanion(booking, GuestType.adult);
		createBookingCompanion(booking, GuestType.child);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/check-in", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bookingId").value(booking.getId().toString()))
				.andExpect(jsonPath("$.status").value("checked_in"))
				.andExpect(jsonPath("$.guestId").value(booking.getGuest().getId().toString()))
				.andExpect(jsonPath("$.guestFirstName").value(booking.getGuest().getFirstName()))
				.andExpect(jsonPath("$.roomId").value(booking.getRoom().getId().toString()))
				.andExpect(jsonPath("$.roomNumber").value(booking.getRoom().getRoomNumber()))
				.andExpect(jsonPath("$.adults").value(2))
				.andExpect(jsonPath("$.children").value(1))
				.andExpect(jsonPath("$.companionCount").value(2))
				.andExpect(jsonPath("$.totalOccupants").value(3))
				.andExpect(jsonPath("$.operationTimestamp").exists());

		assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
				.isEqualTo(BookingStatus.checked_in);
		assertThat(roomRepository.findById(booking.getRoom().getId()).orElseThrow().getStatus())
				.isEqualTo(RoomStatus.occupied);
	}

	@Test
	void checkInMissingBookingReturnsNotFound() throws Exception {
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/check-in", UUID.randomUUID())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Booking not found")));
	}

	@Test
	void checkInStatusNotAllowedReturnsBadRequest() throws Exception {
		Booking booking = createCheckInReadyBooking(2, 1, 0);
		booking.setStatus(BookingStatus.pending);
		bookingRepository.save(booking);

		checkInExpectingBadRequest(booking.getId());
	}

	@Test
	void repeatedCheckInReturnsBadRequest() throws Exception {
		Booking booking = createCheckInReadyBooking(2, 1, 0);
		booking.setStatus(BookingStatus.checked_in);
		booking.getRoom().setStatus(RoomStatus.occupied);
		roomRepository.save(booking.getRoom());
		bookingRepository.save(booking);

		checkInExpectingBadRequest(booking.getId());
	}

	@Test
	void checkInWithoutAssignedRoomReturnsBadRequest() throws Exception {
		Booking booking = createCheckInReadyBooking(2, 1, 0);
		booking.setRoom(null);
		bookingRepository.save(booking);

		checkInExpectingBadRequest(booking.getId());
	}

	@Test
	void checkInWithRoomFromDifferentRoomTypeReturnsBadRequest() throws Exception {
		Booking booking = createCheckInReadyBooking(2, 1, 0);
		RoomType otherRoomType = createRoomTypeWithCapacity(2);
		Room otherRoom = createRoom(otherRoomType);
		booking.setRoom(otherRoom);
		bookingRepository.save(booking);

		checkInExpectingBadRequest(booking.getId());
	}

	@Test
	void checkInWithUnavailableRoomReturnsBadRequest() throws Exception {
		Booking booking = createCheckInReadyBooking(2, 1, 0);
		booking.getRoom().setStatus(RoomStatus.maintenance);
		roomRepository.save(booking.getRoom());

		checkInExpectingBadRequest(booking.getId());
	}

	@Test
	void checkInOutsideStayDatesReturnsBadRequest() throws Exception {
		Booking booking = createCheckInReadyBooking(2, 1, 0);
		booking.setCheckIn(HOTEL_TODAY.plusDays(2));
		booking.setCheckOut(HOTEL_TODAY.plusDays(4));
		bookingRepository.save(booking);

		checkInExpectingBadRequest(booking.getId());
	}

	@Test
	void checkInUsesHotelTimezoneForStayDateWindow() throws Exception {
		Booking booking = createCheckInReadyBooking(2, 1, 0);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/check-in", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkIn").value(HOTEL_TODAY.toString()))
				.andExpect(jsonPath("$.checkOut").value(HOTEL_TODAY.plusDays(1).toString()));
	}

	@Test
	void checkInWithIncompleteCompanionsReturnsBadRequest() throws Exception {
		Booking booking = createCheckInReadyBooking(3, 2, 1);
		createBookingCompanion(booking, GuestType.adult);

		checkInExpectingBadRequest(booking.getId());
	}

	@Test
	void checkInWithCapacityExceededReturnsBadRequest() throws Exception {
		Booking booking = createCheckInReadyBooking(2, 2, 1);
		createBookingCompanion(booking, GuestType.adult);
		createBookingCompanion(booking, GuestType.child);

		checkInExpectingBadRequest(booking.getId());
	}

	@Test
	void failedCheckInDoesNotPartiallyUpdateBookingOrRoom() throws Exception {
		Booking booking = createCheckInReadyBooking(3, 2, 1);
		createBookingCompanion(booking, GuestType.adult);

		checkInExpectingBadRequest(booking.getId());

		assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
				.isEqualTo(BookingStatus.confirmed);
		assertThat(roomRepository.findById(booking.getRoom().getId()).orElseThrow().getStatus())
				.isEqualTo(RoomStatus.available);
	}

	@Test
	void checkInWithInvalidUuidReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/check-in", "BKG-001")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void checkOutClosesZeroBalanceFolioAndMarksRoomAvailableDirty() throws Exception {
		Booking booking = createCheckedInBookingWithFolio(0L);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/check-out", booking.getId())
						.with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(booking.getId().toString()))
				.andExpect(jsonPath("$.status").value("checked_out"));

		assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
				.isEqualTo(BookingStatus.checked_out);
		GuestAccount account = guestAccountRepository.findByBookingId(booking.getId()).orElseThrow();
		assertThat(account.getStatus()).isEqualTo(GuestAccountStatus.closed);
		assertThat(account.getClosedAt()).isNotNull();
		Room room = roomRepository.findById(booking.getRoom().getId()).orElseThrow();
		assertThat(room.getStatus()).isEqualTo(RoomStatus.available);
		assertThat(room.getHousekeepingStatus()).isEqualTo(RoomHousekeepingStatus.dirty);
	}

	@Test
	void checkOutWithPositiveBalanceReturnsConflictAndDoesNotPartiallyUpdate() throws Exception {
		Booking booking = createCheckedInBookingWithFolio(1500L);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/check-out", booking.getId())
						.with(staffUser()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Guest account balance must be zero before checkout"));

		assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
				.isEqualTo(BookingStatus.checked_in);
		assertThat(guestAccountRepository.findByBookingId(booking.getId()).orElseThrow().getStatus())
				.isEqualTo(GuestAccountStatus.open);
		assertThat(roomRepository.findById(booking.getRoom().getId()).orElseThrow().getStatus())
				.isEqualTo(RoomStatus.occupied);
	}

	@Test
	void concurrentCheckInsOnlyAllowOneSuccess() throws Exception {
		Booking booking = createCheckInReadyBooking(2, 1, 0);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Callable<Integer> request = () -> {
				start.await(5, TimeUnit.SECONDS);
				return mockMvc.perform(post("/api/v1/bookings/{bookingId}/check-in", booking.getId())
								.with(staffUser())
								.contentType(MediaType.APPLICATION_JSON)
								.content("{}"))
						.andReturn()
						.getResponse()
						.getStatus();
			};
			Future<Integer> first = executor.submit(request);
			Future<Integer> second = executor.submit(request);

			start.countDown();

			assertThat(first.get(10, TimeUnit.SECONDS))
					.isIn(200, 400);
			assertThat(second.get(10, TimeUnit.SECONDS))
					.isIn(200, 400);
			assertThat(java.util.List.of(first.get(), second.get()))
					.containsExactlyInAnyOrder(200, 400);
		} finally {
			executor.shutdownNow();
		}

		assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
				.isEqualTo(BookingStatus.checked_in);
	}

	private Booking createCheckInReadyBooking(int capacity, int adults, int children) {
		Guest guest = createGuest();
		RoomType roomType = createRoomTypeWithCapacity(capacity);
		Room room = createRoom(roomType);
		Rate rate = createRate(roomType);
		Booking booking = createBooking(guest, roomType, room, rate, adults, children);
		booking.setCheckIn(HOTEL_TODAY);
		booking.setCheckOut(HOTEL_TODAY.plusDays(1));
		booking.setStatus(BookingStatus.confirmed);
		booking.setUpdatedAt(now());
		booking = bookingRepository.save(booking);
		booking.setGuest(guest);
		booking.setRoomType(roomType);
		booking.setRoom(room);
		booking.setRate(rate);
		return booking;
	}

	private Booking createCheckedInBookingWithFolio(long balanceCents) {
		Booking booking = createCheckInReadyBooking(2, 1, 0);
		UUID roomId = booking.getRoom().getId();
		booking.setStatus(BookingStatus.checked_in);
		booking.setUpdatedAt(now());
		booking = bookingRepository.save(booking);
		Room room = roomRepository.findById(roomId).orElseThrow();
		room.setStatus(RoomStatus.occupied);
		roomRepository.save(room);
		booking.setRoom(room);

		OffsetDateTime timestamp = now();
		GuestAccount account = new GuestAccount();
		account.setBooking(booking);
		account.setGuest(booking.getGuest());
		account.setStatus(GuestAccountStatus.open);
		account.setBalanceCents(balanceCents);
		account.setCurrency("GTQ");
		account.setOpenedAt(timestamp);
		account.setCreatedAt(timestamp);
		account.setUpdatedAt(timestamp);
		guestAccountRepository.save(account);
		checkoutBookingIds.add(booking.getId());
		return booking;
	}

	private RoomType createRoomTypeWithCapacity(int capacity) {
		RoomType roomType = createRoomType();
		roomType.setCapacity(capacity);
		roomType.setUpdatedAt(now());
		return roomTypeRepository.save(roomType);
	}

	private void checkInExpectingBadRequest(UUID bookingId) throws Exception {
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/check-in", bookingId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@TestConfiguration
	static class FixedClockConfiguration {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(Instant.parse("2026-03-11T05:30:00Z"), HOTEL_ZONE);
		}
	}
}
