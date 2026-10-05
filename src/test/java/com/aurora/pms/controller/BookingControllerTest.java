package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.RoomStatus;
import com.jayway.jsonpath.JsonPath;

class BookingControllerTest extends AbstractCatalogApiTest {

	@Test
	void listBookingsReturnsOkIncludingExistingBooking() throws Exception {
		Booking booking = createBookingFixture();

		mockMvc.perform(get("/api/v1/bookings").with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(booking.getId().toString())));
	}

	@Test
	void getBookingByIdReturnsBooking() throws Exception {
		Booking booking = createBookingFixture();

		mockMvc.perform(get("/api/v1/bookings/{id}", booking.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(booking.getId().toString()))
				.andExpect(jsonPath("$.confirmationCode").value(booking.getConfirmationCode()))
				.andExpect(jsonPath("$.guestLinkCode").value(booking.getGuestLinkCode()))
				.andExpect(jsonPath("$.guestId").value(booking.getGuest().getId().toString()))
				.andExpect(jsonPath("$.roomId").value(booking.getRoom().getId().toString()))
				.andExpect(jsonPath("$.roomTypeId").value(booking.getRoomType().getId().toString()))
				.andExpect(jsonPath("$.rateId").value(booking.getRate().getId().toString()))
				.andExpect(jsonPath("$.status").value("confirmed"))
				.andExpect(jsonPath("$.totalAmountCents").value(90000))
				.andExpect(jsonPath("$.currency").value("GTQ"));
	}

	@Test
	void getMissingBookingReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/bookings/{id}", UUID.randomUUID()).with(staffUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Booking not found")));
	}

	@Test
	void getBookingWithInvalidUuidReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/bookings/{id}", "BKG-001").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void createBookingReturnsCreatedWithGeneratedFields() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();
		Room room = createRoom(roomType);
		Rate rate = createRate(roomType);
		UUID clientId = UUID.randomUUID();

		MvcResult result = mockMvc.perform(post("/api/v1/bookings")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"id": "%s", "confirmationCode": "CLIENT-CODE", "guestLinkCode": "CLIENT-LINK",
								 "guestId": "%s", "roomTypeId": "%s", "roomId": "%s", "rateId": "%s",
								 "checkIn": "2026-03-15", "checkOut": "2026-03-18",
								 "adults": 2, "children": 0, "notes": "Late arrival",
								 "createdAt": "2000-01-01T00:00:00Z", "updatedAt": "2000-01-01T00:00:00Z"}
								""".formatted(clientId, guest.getId(), roomType.getId(), room.getId(), rate.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(not(clientId.toString())))
				.andExpect(jsonPath("$.confirmationCode").value(not("CLIENT-CODE")))
				.andExpect(jsonPath("$.guestLinkCode").value(not("CLIENT-LINK")))
				.andExpect(jsonPath("$.guestId").value(guest.getId().toString()))
				.andExpect(jsonPath("$.roomTypeId").value(roomType.getId().toString()))
				.andExpect(jsonPath("$.roomId").value(room.getId().toString()))
				.andExpect(jsonPath("$.rateId").value(rate.getId().toString()))
				.andExpect(jsonPath("$.checkIn").value("2026-03-15"))
				.andExpect(jsonPath("$.checkOut").value("2026-03-18"))
				.andExpect(jsonPath("$.status").value("pending"))
				.andExpect(jsonPath("$.totalAmountCents").value(135000))
				.andExpect(jsonPath("$.currency").value("GTQ"))
				.andExpect(jsonPath("$.createdAt").value(not("2000-01-01T00:00:00Z")))
				.andReturn();

		UUID id = trackCreatedBooking(result);
		assertThat(bookingRepository.findById(id)).isPresent();
	}

	@Test
	void createBookingWithoutRoomOrRateReturnsCreatedWithZeroAmount() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();

		MvcResult result = mockMvc.perform(post("/api/v1/bookings")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"guestId": "%s", "roomTypeId": "%s", "checkIn": "2026-03-15",
								 "checkOut": "2026-03-16", "adults": 1, "children": 0}
								""".formatted(guest.getId(), roomType.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.roomId").doesNotExist())
				.andExpect(jsonPath("$.rateId").doesNotExist())
				.andExpect(jsonPath("$.totalAmountCents").value(0))
				.andReturn();

		trackCreatedBooking(result);
	}

	@Test
	void updateBookingAppliesPartialChanges() throws Exception {
		Booking booking = createBookingFixture();
		String before = mockMvc.perform(get("/api/v1/bookings/{id}", booking.getId()).with(staffUser()))
				.andReturn().getResponse().getContentAsString();

		mockMvc.perform(put("/api/v1/bookings/{id}", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"checkIn": "2026-03-20", "checkOut": "2026-03-23",
								 "adults": 2, "children": 0, "notes": "Updated"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(booking.getId().toString()))
				.andExpect(jsonPath("$.checkIn").value("2026-03-20"))
				.andExpect(jsonPath("$.checkOut").value("2026-03-23"))
				.andExpect(jsonPath("$.status").value("confirmed"))
				.andExpect(jsonPath("$.totalAmountCents").value(135000))
				.andExpect(jsonPath("$.confirmationCode").value((String) JsonPath.read(before, "$.confirmationCode")))
				.andExpect(jsonPath("$.guestLinkCode").value((String) JsonPath.read(before, "$.guestLinkCode")))
				.andExpect(jsonPath("$.createdAt").value((String) JsonPath.read(before, "$.createdAt")))
				.andExpect(jsonPath("$.updatedAt").value(not((String) JsonPath.read(before, "$.updatedAt"))));
	}

	@Test
	void updateBookingRejectsStatusChanges() throws Exception {
		Booking booking = createBookingFixture();

		mockMvc.perform(put("/api/v1/bookings/{id}", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\": \"cancelled\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void confirmPendingBookingChangesStatus() throws Exception {
		Booking booking = createBookingFixture();
		booking.setStatus(BookingStatus.pending);
		bookingRepository.save(booking);

		mockMvc.perform(post("/api/v1/bookings/{id}/confirm", booking.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(booking.getId().toString()))
				.andExpect(jsonPath("$.status").value("confirmed"));

		assertThat(bookingRepository.findById(booking.getId()))
				.get().extracting(Booking::getStatus).isEqualTo(BookingStatus.confirmed);
	}

	@Test
	void confirmNonPendingBookingReturnsBadRequest() throws Exception {
		Booking booking = createBookingFixture();

		mockMvc.perform(post("/api/v1/bookings/{id}/confirm", booking.getId()).with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Only pending bookings can be confirmed"));
	}

	@Test
	void cancelConfirmedBookingPersistsReason() throws Exception {
		Booking booking = createBookingFixture();

		mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": "  Guest requested cancellation  "}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("cancelled"))
				.andExpect(jsonPath("$.cancellationReason").value("Guest requested cancellation"))
				.andExpect(jsonPath("$.cancelledAt").exists());

		assertThat(bookingRepository.findById(booking.getId())).get()
				.satisfies(stored -> {
					assertThat(stored.getStatus()).isEqualTo(BookingStatus.cancelled);
					assertThat(stored.getCancellationReason()).isEqualTo("Guest requested cancellation");
					assertThat(stored.getCancelledAt()).isNotNull();
				});
	}

	@Test
	void cancelRequiresReasonAndAllowedStatus() throws Exception {
		Booking booking = createBookingFixture();

		mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": " "}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.reason").exists());

		booking.setStatus(BookingStatus.checked_in);
		bookingRepository.save(booking);
		mockMvc.perform(post("/api/v1/bookings/{id}/cancel", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": "No longer valid"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Only pending or confirmed bookings can be cancelled"));
	}

	@Test
	void updateCheckedInBookingRejectsStructuralChanges() throws Exception {
		Booking booking = createBookingFixture();
		booking.setStatus(BookingStatus.checked_in);
		bookingRepository.save(booking);

		mockMvc.perform(put("/api/v1/bookings/{id}", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"checkOut\": \"2026-03-13\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void updateMissingBookingReturnsNotFound() throws Exception {
		mockMvc.perform(put("/api/v1/bookings/{id}", UUID.randomUUID())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"notes\": \"Missing\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void updateBookingWithInactiveRateReturnsBadRequest() throws Exception {
		Booking booking = createBookingFixture();
		Rate inactiveRate = createRate(booking.getRoomType());
		inactiveRate.setActive(false);
		rateRepository.save(inactiveRate);

		mockMvc.perform(put("/api/v1/bookings/{id}", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"rateId\": \"%s\"}".formatted(inactiveRate.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void createBookingWithInvalidDatesReturnsBadRequest() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();

		createBookingExpectingBadRequest("""
				{"guestId": "%s", "roomTypeId": "%s", "checkIn": "2026-03-18",
				 "checkOut": "2026-03-18", "adults": 1, "children": 0}
				""".formatted(guest.getId(), roomType.getId()));
	}

	@Test
	void createBookingWithInvalidAdultsOrChildrenReturnsBadRequest() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();

		mockMvc.perform(post("/api/v1/bookings")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"guestId": "%s", "roomTypeId": "%s", "checkIn": "2026-03-15",
								 "checkOut": "2026-03-18", "adults": 0, "children": -1}
								""".formatted(guest.getId(), roomType.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.adults").exists())
				.andExpect(jsonPath("$.errors.children").exists());
	}

	@Test
	void createBookingExceedingCapacityReturnsBadRequest() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();

		createBookingExpectingBadRequest("""
				{"guestId": "%s", "roomTypeId": "%s", "checkIn": "2026-03-15",
				 "checkOut": "2026-03-18", "adults": 2, "children": 1}
				""".formatted(guest.getId(), roomType.getId()));
	}

	@Test
	void createBookingWithMissingRelatedResourcesReturnsBadRequest() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();

		createBookingExpectingBadRequest("""
				{"guestId": "%s", "roomTypeId": "%s", "checkIn": "2026-03-15",
				 "checkOut": "2026-03-18", "adults": 1, "children": 0}
				""".formatted(UUID.randomUUID(), roomType.getId()));

		createBookingExpectingBadRequest("""
				{"guestId": "%s", "roomTypeId": "%s", "checkIn": "2026-03-15",
				 "checkOut": "2026-03-18", "adults": 1, "children": 0}
				""".formatted(guest.getId(), UUID.randomUUID()));

		createBookingExpectingBadRequest("""
				{"guestId": "%s", "roomTypeId": "%s", "roomId": "%s", "checkIn": "2026-03-15",
				 "checkOut": "2026-03-18", "adults": 1, "children": 0}
				""".formatted(guest.getId(), roomType.getId(), UUID.randomUUID()));

		createBookingExpectingBadRequest("""
				{"guestId": "%s", "roomTypeId": "%s", "rateId": "%s", "checkIn": "2026-03-15",
				 "checkOut": "2026-03-18", "adults": 1, "children": 0}
				""".formatted(guest.getId(), roomType.getId(), UUID.randomUUID()));
	}

	@Test
	void createBookingWithRoomOrRateForDifferentRoomTypeReturnsBadRequest() throws Exception {
		Guest guest = createGuest();
		RoomType requestedRoomType = createRoomType();
		RoomType otherRoomType = createRoomType();
		Room otherRoom = createRoom(otherRoomType);
		Rate otherRate = createRate(otherRoomType);

		createBookingExpectingBadRequest("""
				{"guestId": "%s", "roomTypeId": "%s", "roomId": "%s", "checkIn": "2026-03-15",
				 "checkOut": "2026-03-18", "adults": 1, "children": 0}
				""".formatted(guest.getId(), requestedRoomType.getId(), otherRoom.getId()));

		createBookingExpectingBadRequest("""
				{"guestId": "%s", "roomTypeId": "%s", "rateId": "%s", "checkIn": "2026-03-15",
				 "checkOut": "2026-03-18", "adults": 1, "children": 0}
				""".formatted(guest.getId(), requestedRoomType.getId(), otherRate.getId()));
	}

	@Test
	void createBookingWithOverlappingRoomReturnsBadRequest() throws Exception {
		Booking existing = createBookingFixture();

		createBookingExpectingBadRequest("""
				{"guestId": "%s", "roomTypeId": "%s", "roomId": "%s", "rateId": "%s",
				 "checkIn": "2026-03-11", "checkOut": "2026-03-13", "adults": 1, "children": 0}
				""".formatted(existing.getGuest().getId(), existing.getRoomType().getId(),
				existing.getRoom().getId(), existing.getRate().getId()));
	}

	@Test
	void createBookingWithActiveRateValidForStayReturnsCreated() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();
		Rate rate = createRate(roomType);

		MvcResult result = mockMvc.perform(post("/api/v1/bookings")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"guestId": "%s", "roomTypeId": "%s", "rateId": "%s",
								 "checkIn": "2026-03-15", "checkOut": "2026-03-18",
								 "adults": 1, "children": 0}
								""".formatted(guest.getId(), roomType.getId(), rate.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.rateId").value(rate.getId().toString()))
				.andExpect(jsonPath("$.totalAmountCents").value(135000))
				.andReturn();

		trackCreatedBooking(result);
	}

	@Test
	void createBookingWithInactiveRateReturnsBadRequest() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();
		Rate rate = createRate(roomType);
		rate.setActive(false);
		rateRepository.save(rate);

		createBookingExpectingBadRequest("""
				{"guestId": "%s", "roomTypeId": "%s", "rateId": "%s",
				 "checkIn": "2026-03-15", "checkOut": "2026-03-18", "adults": 1, "children": 0}
				""".formatted(guest.getId(), roomType.getId(), rate.getId()));
	}

	@Test
	void createBookingWithRateOutsideValidityReturnsBadRequest() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();
		Rate rate = createRate(roomType);
		rate.setValidTo(LocalDate.of(2026, 3, 16));
		rateRepository.save(rate);

		createBookingExpectingBadRequest("""
				{"guestId": "%s", "roomTypeId": "%s", "rateId": "%s",
				 "checkIn": "2026-03-15", "checkOut": "2026-03-18", "adults": 1, "children": 0}
				""".formatted(guest.getId(), roomType.getId(), rate.getId()));
	}

	@Test
	void createBookingBelowRateMinimumNightsReturnsBadRequest() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();
		Rate rate = createRate(roomType);
		rate.setMinimumNights(4);
		rateRepository.save(rate);

		createBookingExpectingBadRequest("""
				{"guestId": "%s", "roomTypeId": "%s", "rateId": "%s",
				 "checkIn": "2026-03-15", "checkOut": "2026-03-18", "adults": 1, "children": 0}
				""".formatted(guest.getId(), roomType.getId(), rate.getId()));
	}

	@Test
	void createBookingWithMaintenanceRoomReturnsBadRequest() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();
		Room room = createRoom(roomType);
		room.setStatus(RoomStatus.maintenance);
		roomRepository.save(room);

		createBookingExpectingBadRequest("""
				{"guestId": "%s", "roomTypeId": "%s", "roomId": "%s",
				 "checkIn": "2026-03-15", "checkOut": "2026-03-18", "adults": 1, "children": 0}
				""".formatted(guest.getId(), roomType.getId(), room.getId()));
	}

	@Test
	void createBookingWithOutOfServiceRoomReturnsBadRequest() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();
		Room room = createRoom(roomType);
		room.setStatus(RoomStatus.out_of_service);
		roomRepository.save(room);

		createBookingExpectingBadRequest("""
				{"guestId": "%s", "roomTypeId": "%s", "roomId": "%s",
				 "checkIn": "2026-03-15", "checkOut": "2026-03-18", "adults": 1, "children": 0}
				""".formatted(guest.getId(), roomType.getId(), room.getId()));
	}

	private Booking createBookingFixture() {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();
		Room room = createRoom(roomType);
		Rate rate = createRate(roomType);
		return createBooking(guest, roomType, room, rate);
	}

	private void createBookingExpectingBadRequest(String body) throws Exception {
		mockMvc.perform(post("/api/v1/bookings")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}
}
