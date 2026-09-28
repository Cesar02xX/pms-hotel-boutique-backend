package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.BookingCompanion;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.GuestType;
import com.jayway.jsonpath.JsonPath;

class BookingCompanionControllerTest extends AbstractCatalogApiTest {

	@Test
	void listCompanionsReturnsOkIncludingExistingCompanion() throws Exception {
		Booking booking = createBookingFixture(3, 2, 1);
		BookingCompanion adult = createBookingCompanion(booking, GuestType.adult);
		BookingCompanion child = createBookingCompanion(booking, GuestType.child);

		mockMvc.perform(get("/api/v1/bookings/{bookingId}/companions", booking.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(adult.getId().toString())))
				.andExpect(jsonPath("$[*].id", hasItem(child.getId().toString())));
	}

	@Test
	void createCompanionReturnsCreated() throws Exception {
		Booking booking = createBookingFixture(3, 2, 1);

		MvcResult result = mockMvc.perform(post("/api/v1/bookings/{bookingId}/companions", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": "Ana", "lastName": "Lopez", "documentType": "passport",
								 "documentNumber": "P-123", "guestType": "adult",
								 "id": "%s", "createdAt": "2000-01-01T00:00:00Z"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.bookingId").value(booking.getId().toString()))
				.andExpect(jsonPath("$.firstName").value("Ana"))
				.andExpect(jsonPath("$.lastName").value("Lopez"))
				.andExpect(jsonPath("$.documentType").value("passport"))
				.andExpect(jsonPath("$.documentNumber").value("P-123"))
				.andExpect(jsonPath("$.guestType").value("adult"))
				.andExpect(jsonPath("$.createdAt").value(not("2000-01-01T00:00:00Z")))
				.andReturn();

		UUID id = trackCreatedBookingCompanion(result);
		assertThat(bookingCompanionRepository.findById(id)).isPresent();
	}

	@Test
	void updateCompanionReturnsOkAndKeepsServerFields() throws Exception {
		Booking booking = createBookingFixture(3, 2, 1);
		BookingCompanion companion = createBookingCompanion(booking, GuestType.adult);
		String before = mockMvc.perform(get("/api/v1/bookings/{bookingId}/companions", booking.getId())
						.with(staffUser()))
				.andReturn().getResponse().getContentAsString();
		String createdAt = JsonPath.read(before, "$[0].createdAt");

		mockMvc.perform(put("/api/v1/bookings/{bookingId}/companions/{companionId}",
						booking.getId(), companion.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": "Carlos", "lastName": "Perez", "documentType": "national_id",
								 "documentNumber": "DPI-1", "guestType": "child"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(companion.getId().toString()))
				.andExpect(jsonPath("$.bookingId").value(booking.getId().toString()))
				.andExpect(jsonPath("$.firstName").value("Carlos"))
				.andExpect(jsonPath("$.lastName").value("Perez"))
				.andExpect(jsonPath("$.documentType").value("national_id"))
				.andExpect(jsonPath("$.guestType").value("child"))
				.andExpect(jsonPath("$.createdAt").value(createdAt));
	}

	@Test
	void deleteCompanionReturnsNoContent() throws Exception {
		Booking booking = createBookingFixture(3, 2, 1);
		BookingCompanion companion = createBookingCompanion(booking, GuestType.adult);

		mockMvc.perform(delete("/api/v1/bookings/{bookingId}/companions/{companionId}",
						booking.getId(), companion.getId()).with(staffUser()))
				.andExpect(status().isNoContent());

		assertThat(bookingCompanionRepository.findById(companion.getId())).isEmpty();
	}

	@Test
	void missingBookingReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/bookings/{bookingId}/companions", UUID.randomUUID()).with(staffUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Booking not found")));
	}

	@Test
	void missingCompanionReturnsNotFound() throws Exception {
		Booking booking = createBookingFixture(3, 2, 1);

		mockMvc.perform(put("/api/v1/bookings/{bookingId}/companions/{companionId}",
						booking.getId(), UUID.randomUUID())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(validAdultBody()))
				.andExpect(status().isNotFound());
	}

	@Test
	void companionFromAnotherBookingReturnsNotFound() throws Exception {
		Booking firstBooking = createBookingFixture(3, 2, 1);
		Booking secondBooking = createBookingFixture(3, 2, 1);
		BookingCompanion companion = createBookingCompanion(firstBooking, GuestType.adult);

		mockMvc.perform(put("/api/v1/bookings/{bookingId}/companions/{companionId}",
						secondBooking.getId(), companion.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(validAdultBody()))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/v1/bookings/{bookingId}/companions/{companionId}",
						secondBooking.getId(), companion.getId()).with(staffUser()))
				.andExpect(status().isNotFound());
	}

	@Test
	void capacityExceededReturnsBadRequest() throws Exception {
		Booking booking = createBookingFixture(2, 2, 1);
		createBookingCompanion(booking, GuestType.adult);

		createCompanionExpectingBadRequest(booking, """
				{"firstName": "Child", "lastName": "Guest", "guestType": "child"}
				""");
	}

	@Test
	void adultLimitExceededReturnsBadRequest() throws Exception {
		Booking booking = createBookingFixture(3, 1, 1);

		createCompanionExpectingBadRequest(booking, validAdultBody());
	}

	@Test
	void childLimitExceededReturnsBadRequest() throws Exception {
		Booking booking = createBookingFixture(3, 1, 0);

		createCompanionExpectingBadRequest(booking, """
				{"firstName": "Child", "lastName": "Guest", "guestType": "child"}
				""");
	}

	@Test
	void primaryGuestCannotBeRegisteredAsCompanion() throws Exception {
		Booking booking = createBookingFixture(3, 2, 1);

		createCompanionExpectingBadRequest(booking, """
				{"firstName": "%s", "lastName": "%s", "documentNumber": "%s", "guestType": "adult"}
				""".formatted(
				booking.getGuest().getFirstName(),
				booking.getGuest().getLastName(),
				booking.getGuest().getDocumentNumber()));
	}

	@Test
	void blankNamesReturnBadRequest() throws Exception {
		Booking booking = createBookingFixture(3, 2, 1);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/companions", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": " ", "lastName": "", "guestType": "adult"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.firstName").exists())
				.andExpect(jsonPath("$.errors.lastName").exists());
	}

	@Test
	void invalidEnumReturnsBadRequest() throws Exception {
		Booking booking = createBookingFixture(3, 2, 1);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/companions", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": "Ana", "lastName": "Lopez", "guestType": "teen"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void invalidUuidReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/bookings/{bookingId}/companions", "BKG-001").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	private Booking createBookingFixture(int capacity, int adults, int children) {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();
		roomType.setCapacity(capacity);
		roomType = roomTypeRepository.save(roomType);
		Room room = createRoom(roomType);
		Rate rate = createRate(roomType);
		return createBooking(guest, roomType, room, rate, adults, children);
	}

	private void createCompanionExpectingBadRequest(Booking booking, String body) throws Exception {
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/companions", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	private static String validAdultBody() {
		return """
				{"firstName": "Adult", "lastName": "Guest", "guestType": "adult"}
				""";
	}
}
