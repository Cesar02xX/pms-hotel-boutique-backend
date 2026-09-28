package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.GuestType;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.RoomStatus;

class BookingCheckInControllerTest extends AbstractCatalogApiTest {

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
		booking.setCheckIn(LocalDate.now().plusDays(2));
		booking.setCheckOut(LocalDate.now().plusDays(4));
		bookingRepository.save(booking);

		checkInExpectingBadRequest(booking.getId());
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

	private Booking createCheckInReadyBooking(int capacity, int adults, int children) {
		Guest guest = createGuest();
		RoomType roomType = createRoomTypeWithCapacity(capacity);
		Room room = createRoom(roomType);
		Rate rate = createRate(roomType);
		Booking booking = createBooking(guest, roomType, room, rate, adults, children);
		booking.setCheckIn(LocalDate.now());
		booking.setCheckOut(LocalDate.now().plusDays(1));
		booking.setStatus(BookingStatus.confirmed);
		booking.setUpdatedAt(now());
		booking = bookingRepository.save(booking);
		booking.setGuest(guest);
		booking.setRoomType(roomType);
		booking.setRoom(room);
		booking.setRate(rate);
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
}
