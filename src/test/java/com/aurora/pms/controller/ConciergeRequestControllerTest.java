package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Role;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;
import com.aurora.pms.model.enums.UserStatus;
import com.aurora.pms.repository.ChargeRepository;
import com.aurora.pms.repository.RoleRepository;
import com.aurora.pms.repository.ServiceRequestRepository;
import com.aurora.pms.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

class ConciergeRequestControllerTest extends AbstractCatalogApiTest {

	private static final String BASE_PATH = "/api/v1/concierge/requests";

	@Autowired
	private ServiceRequestRepository serviceRequestRepository;

	@Autowired
	private ChargeRepository chargeRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	private final List<UUID> requestIds = new ArrayList<>();
	private final List<UUID> userIds = new ArrayList<>();
	private final List<UUID> roleIds = new ArrayList<>();

	/** Corre antes del cleanup de la clase base, que borra las reservas. */
	@AfterEach
	void cleanUpConciergeData() {
		serviceRequestRepository.deleteAllById(requestIds);
		userRepository.deleteAllById(userIds);
		roleRepository.deleteAllById(roleIds);
	}

	// ---------- Create

	@Test
	void createReturnsCreatedAndIgnoresServerFields() throws Exception {
		Booking booking = createConciergeBooking();

		MvcResult result = mockMvc.perform(post(BASE_PATH)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookingId": "%s", "description": " Reservar cena para 2 ", "notes": " Mesa terraza ",
								 "id": "%s", "type": "housekeeping", "status": "completed",
								 "roomId": "%s", "guestId": "%s", "responsibleUserId": "%s", "chargeId": "%s",
								 "requestedAt": "2000-01-01T00:00:00Z", "createdAt": "2000-01-01T00:00:00Z"}
								""".formatted(booking.getId(), UUID.randomUUID(), UUID.randomUUID(),
								UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.bookingId").value(booking.getId().toString()))
				.andExpect(jsonPath("$.roomId").value(booking.getRoom().getId().toString()))
				.andExpect(jsonPath("$.guestId").value(booking.getGuest().getId().toString()))
				.andExpect(jsonPath("$.responsibleUserId").value(nullValue()))
				.andExpect(jsonPath("$.type").value("concierge"))
				.andExpect(jsonPath("$.status").value("pending"))
				.andExpect(jsonPath("$.description").value("Reservar cena para 2"))
				.andExpect(jsonPath("$.notes").value("Mesa terraza"))
				.andExpect(jsonPath("$.chargeId").value(nullValue()))
				.andExpect(jsonPath("$.requestedAt").value(not("2000-01-01T00:00:00Z")))
				.andExpect(jsonPath("$.createdAt").value(not("2000-01-01T00:00:00Z")))
				.andReturn();

		UUID id = trackRequest(result);
		ServiceRequest stored = serviceRequestRepository.findById(id).orElseThrow();
		assertThat(stored.getType()).isEqualTo(ServiceRequestType.concierge);
		assertThat(stored.getStatus()).isEqualTo(ServiceRequestStatus.pending);
		assertThat(stored.getCharge()).isNull();
		assertThat(chargeRepository.findByBookingIdOrderByChargedAtAscCreatedAtAsc(booking.getId())).isEmpty();
	}

	@Test
	void createForBookingWithoutRoomLeavesRoomEmpty() throws Exception {
		RoomType roomType = createRoomType();
		Booking booking = createBooking(createGuest(), roomType, null, createRate(roomType));

		mockMvc.perform(post(BASE_PATH)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody(booking.getId(), "Taxi al aeropuerto")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.roomId").value(nullValue()))
				.andExpect(jsonPath("$.guestId").value(booking.getGuest().getId().toString()))
				.andDo(result -> trackRequest(result));
	}

	@Test
	void createWithMissingBookingReturnsBadRequest() throws Exception {
		mockMvc.perform(post(BASE_PATH)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody(UUID.randomUUID(), "Taxi")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith("Booking not found")));
	}

	@ParameterizedTest
	@EnumSource(value = BookingStatus.class, names = {"pending", "checked_out", "cancelled", "no_show"})
	void createForDisallowedBookingStatusReturnsBadRequest(BookingStatus bookingStatus) throws Exception {
		Booking booking = createConciergeBooking();
		booking.setStatus(bookingStatus);
		bookingRepository.save(booking);

		mockMvc.perform(post(BASE_PATH)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(createBody(booking.getId(), "Taxi")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("Cannot create concierge requests for a booking with status " + bookingStatus));
	}

	@Test
	void createWithInvalidBodyReturnsBadRequest() throws Exception {
		Booking booking = createConciergeBooking();

		for (String description : List.of("\"\"", "\"   \"", "null")) {
			mockMvc.perform(post(BASE_PATH)
							.with(staffUser())
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"bookingId": "%s", "description": %s}
									""".formatted(booking.getId(), description)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.description").exists());
		}
		mockMvc.perform(post(BASE_PATH)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description": "Taxi"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.bookingId").exists());
		mockMvc.perform(post(BASE_PATH)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookingId": "not-a-uuid", "description": "Taxi"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Malformed or invalid request body"));

		assertThat(serviceRequestRepository.search(ServiceRequestType.concierge, booking.getId(), null)).isEmpty();
	}

	// ---------- List / detail

	@Test
	void listReturnsOnlyConciergeRequests() throws Exception {
		Booking booking = createConciergeBooking();
		String concierge = createRequest(booking, "Tour por la ciudad");
		UUID housekeeping = createServiceRequestOfType(booking, ServiceRequestType.housekeeping);
		UUID maintenance = createServiceRequestOfType(booking, ServiceRequestType.maintenance);

		mockMvc.perform(get(BASE_PATH).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].type", everyItem(is("concierge"))))
				.andExpect(jsonPath("$[*].id", hasItem(concierge)))
				.andExpect(jsonPath("$[*].id", not(hasItem(housekeeping.toString()))))
				.andExpect(jsonPath("$[*].id", not(hasItem(maintenance.toString()))));

		mockMvc.perform(get(BASE_PATH).param("bookingId", booking.getId().toString()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(concierge));
	}

	@Test
	void listFiltersByBookingAndStatus() throws Exception {
		Booking booking = createConciergeBooking();
		Booking otherBooking = createConciergeBooking();
		String first = createRequest(booking, "Primera");
		String second = createRequest(booking, "Segunda");
		createRequest(otherBooking, "Otra reserva");
		changeStatus(second, "accepted");

		mockMvc.perform(get(BASE_PATH).param("bookingId", booking.getId().toString()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id").value(first))
				.andExpect(jsonPath("$[1].id").value(second));

		mockMvc.perform(get(BASE_PATH)
						.param("bookingId", booking.getId().toString())
						.param("status", "accepted")
						.with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(second));

		mockMvc.perform(get(BASE_PATH).param("status", "pending").with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].status", everyItem(is("pending"))))
				.andExpect(jsonPath("$[*].id", hasItem(first)))
				.andExpect(jsonPath("$[*].id", not(hasItem(second))));
	}

	@Test
	void invalidFiltersReturnBadRequest() throws Exception {
		mockMvc.perform(get(BASE_PATH).param("status", "done").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid value for parameter 'status'"));
		mockMvc.perform(get(BASE_PATH).param("bookingId", "not-a-uuid").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid value for parameter 'bookingId'"));
	}

	@Test
	void findByIdReturnsConciergeRequest() throws Exception {
		Booking booking = createConciergeBooking();
		String id = createRequest(booking, "Flores en la habitación");

		mockMvc.perform(get(BASE_PATH + "/{requestId}", id).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.description").value("Flores en la habitación"))
				.andExpect(jsonPath("$.type").value("concierge"));
	}

	@Test
	void requestOfAnotherTypeIsNotFound() throws Exception {
		Booking booking = createConciergeBooking();
		UUID housekeeping = createServiceRequestOfType(booking, ServiceRequestType.housekeeping);

		mockMvc.perform(get(BASE_PATH + "/{requestId}", housekeeping).with(staffUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Concierge request not found")));
		mockMvc.perform(post(BASE_PATH + "/{requestId}/status", housekeeping)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(statusBody("accepted")))
				.andExpect(status().isNotFound());

		assertThat(serviceRequestRepository.findById(housekeeping)).get()
				.satisfies(request -> assertThat(request.getStatus()).isEqualTo(ServiceRequestStatus.pending));
	}

	@Test
	void missingRequestReturnsNotFound() throws Exception {
		UUID id = UUID.randomUUID();

		mockMvc.perform(get(BASE_PATH + "/{requestId}", id).with(staffUser()))
				.andExpect(status().isNotFound());
		mockMvc.perform(post(BASE_PATH + "/{requestId}/status", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(statusBody("accepted")))
				.andExpect(status().isNotFound());
	}

	@Test
	void invalidUuidReturnsBadRequest() throws Exception {
		mockMvc.perform(get(BASE_PATH + "/{requestId}", "not-a-uuid").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid value for parameter 'requestId'"));
		mockMvc.perform(post(BASE_PATH + "/{requestId}/status", "not-a-uuid")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(statusBody("accepted")))
				.andExpect(status().isBadRequest());
	}

	// ---------- Status flow

	@Test
	void statusChangeCanAssignResponsibleUser() throws Exception {
		String id = createRequest(createConciergeBooking(), "Reservar tour");
		User responsible = createResponsibleUser();

		mockMvc.perform(post(BASE_PATH + "/{requestId}/status", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "accepted", "responsibleUserId": "%s"}
								""".formatted(responsible.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("accepted"))
				.andExpect(jsonPath("$.responsibleUserId").value(responsible.getId().toString()));

		assertThat(serviceRequestRepository.findById(UUID.fromString(id))).get()
				.satisfies(request -> assertThat(request.getResponsibleUser().getId()).isEqualTo(responsible.getId()));
	}

	@Test
	void statusChangeWithMissingResponsibleUserReturnsBadRequest() throws Exception {
		String id = createRequest(createConciergeBooking(), "Reservar tour");
		UUID missingUserId = UUID.randomUUID();

		mockMvc.perform(post(BASE_PATH + "/{requestId}/status", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "accepted", "responsibleUserId": "%s"}
								""".formatted(missingUserId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Responsible user not found: " + missingUserId));
	}

	@Test
	void fullFlowReachesCompletedAndAppendsNotes() throws Exception {
		Booking booking = createConciergeBooking();
		String id = createRequest(booking, "Reservar spa", "Pedido en recepción");

		changeStatus(id, "accepted");
		changeStatus(id, "in_progress");
		mockMvc.perform(post(BASE_PATH + "/{requestId}/status", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "completed", "notes": " Confirmado 15:00 "}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("completed"))
				.andExpect(jsonPath("$.notes").value("Pedido en recepción\nConfirmado 15:00"));

		ServiceRequest stored = serviceRequestRepository.findById(UUID.fromString(id)).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(ServiceRequestStatus.completed);
		assertThat(stored.getUpdatedAt()).isAfterOrEqualTo(stored.getCreatedAt());
		assertThat(stored.getCharge()).isNull();
		assertThat(chargeRepository.findByBookingIdOrderByChargedAtAscCreatedAtAsc(booking.getId())).isEmpty();
	}

	static Stream<Arguments> validTransitions() {
		return Stream.of(
				Arguments.of(List.of(), "accepted"),
				Arguments.of(List.of(), "rejected"),
				Arguments.of(List.of(), "cancelled"),
				Arguments.of(List.of("accepted"), "in_progress"),
				Arguments.of(List.of("accepted"), "cancelled"),
				Arguments.of(List.of("accepted", "in_progress"), "cancelled"),
				Arguments.of(List.of("accepted", "in_progress"), "completed")
		);
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@MethodSource("validTransitions")
	void validTransitionsAreAllowed(List<String> path, String target) throws Exception {
		String id = createRequest(createConciergeBooking(), "Solicitud");
		for (String step : path) {
			changeStatus(id, step);
		}

		mockMvc.perform(post(BASE_PATH + "/{requestId}/status", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(statusBody(target)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(target));
	}

	static Stream<Arguments> invalidTransitions() {
		return Stream.of(
				Arguments.of(List.of(), "pending"),
				Arguments.of(List.of(), "in_progress"),
				Arguments.of(List.of(), "completed"),
				Arguments.of(List.of("accepted"), "accepted"),
				Arguments.of(List.of("accepted"), "pending"),
				Arguments.of(List.of("accepted"), "completed"),
				Arguments.of(List.of("accepted"), "rejected"),
				Arguments.of(List.of("accepted", "in_progress"), "rejected"),
				Arguments.of(List.of("accepted", "in_progress"), "pending"),
				Arguments.of(List.of("accepted", "in_progress"), "accepted")
		);
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@MethodSource("invalidTransitions")
	void invalidTransitionsReturnBadRequest(List<String> path, String target) throws Exception {
		String id = createRequest(createConciergeBooking(), "Solicitud");
		for (String step : path) {
			changeStatus(id, step);
		}
		String current = path.isEmpty() ? "pending" : path.get(path.size() - 1);

		mockMvc.perform(post(BASE_PATH + "/{requestId}/status", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(statusBody(target)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("Invalid status transition from " + current + " to " + target));

		assertThat(serviceRequestRepository.findById(UUID.fromString(id))).get()
				.satisfies(request -> assertThat(request.getStatus().name()).isEqualTo(current));
	}

	static Stream<Arguments> terminalStates() {
		return Stream.of(
				Arguments.of(List.of("accepted", "in_progress", "completed"), "completed"),
				Arguments.of(List.of("rejected"), "rejected"),
				Arguments.of(List.of("cancelled"), "cancelled")
		);
	}

	@ParameterizedTest(name = "{1} is terminal")
	@MethodSource("terminalStates")
	void terminalStatesCannotChange(List<String> path, String terminal) throws Exception {
		String id = createRequest(createConciergeBooking(), "Solicitud");
		for (String step : path) {
			changeStatus(id, step);
		}

		for (ServiceRequestStatus target : ServiceRequestStatus.values()) {
			mockMvc.perform(post(BASE_PATH + "/{requestId}/status", id)
							.with(staffUser())
							.contentType(MediaType.APPLICATION_JSON)
							.content(statusBody(target.name())))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("Concierge request is already " + terminal));
		}
	}

	@Test
	void invalidStatusBodyReturnsBadRequest() throws Exception {
		String id = createRequest(createConciergeBooking(), "Solicitud");

		mockMvc.perform(post(BASE_PATH + "/{requestId}/status", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.status").exists());
		mockMvc.perform(post(BASE_PATH + "/{requestId}/status", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(statusBody("done")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Malformed or invalid request body"));
	}

	@Test
	void updatePendingRequestChangesDescriptionAndNotes() throws Exception {
		String id = createRequest(createConciergeBooking(), "Solicitud");

		mockMvc.perform(put(BASE_PATH + "/{requestId}", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description": " Tour privado ", "notes": " Ventana 10:00 "}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value("Tour privado"))
				.andExpect(jsonPath("$.notes").value("Ventana 10:00"));
	}

	@Test
	void updateNonPendingRequestReturnsBadRequest() throws Exception {
		String id = createRequest(createConciergeBooking(), "Solicitud");
		changeStatus(id, "accepted");

		mockMvc.perform(put(BASE_PATH + "/{requestId}", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description": "Tour privado"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Only pending concierge requests can be edited"));
	}

	// ---------- Security

	static Stream<Arguments> conciergeEndpoints() {
		String id = UUID.randomUUID().toString();
		return Stream.of(
				Arguments.of(HttpMethod.GET, BASE_PATH),
				Arguments.of(HttpMethod.GET, BASE_PATH + "/" + id),
				Arguments.of(HttpMethod.POST, BASE_PATH),
				Arguments.of(HttpMethod.POST, BASE_PATH + "/" + id + "/status"),
				Arguments.of(HttpMethod.PUT, BASE_PATH + "/" + id)
		);
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("conciergeEndpoints")
	void endpointWithoutTokenReturnsUnauthorized(HttpMethod method, String path) throws Exception {
		mockMvc.perform(request(method, path)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("conciergeEndpoints")
	void endpointWithInvalidTokenReturnsUnauthorized(HttpMethod method, String path) throws Exception {
		mockMvc.perform(request(method, path)
						.header("Authorization", "Bearer invalid.jwt.token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized());
	}

	// ---------- Helpers

	private Booking createConciergeBooking() {
		RoomType roomType = createRoomType();
		return createBooking(createGuest(), roomType, createRoom(roomType), createRate(roomType));
	}

	private UUID trackRequest(MvcResult result) throws Exception {
		UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
		requestIds.add(id);
		return id;
	}

	private String createRequest(Booking booking, String description) throws Exception {
		return createRequest(booking, description, null);
	}

	private String createRequest(Booking booking, String description, String notes) throws Exception {
		String body = notes == null
				? createBody(booking.getId(), description)
				: """
						{"bookingId": "%s", "description": "%s", "notes": "%s"}
						""".formatted(booking.getId(), description, notes);
		MvcResult result = mockMvc.perform(post(BASE_PATH)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn();
		return trackRequest(result).toString();
	}

	private void changeStatus(String id, String status) throws Exception {
		mockMvc.perform(post(BASE_PATH + "/{requestId}/status", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(statusBody(status)))
				.andExpect(status().isOk());
	}

	private UUID createServiceRequestOfType(Booking booking, ServiceRequestType type) {
		ServiceRequest request = new ServiceRequest();
		request.setBooking(booking);
		request.setRoom(booking.getRoom());
		request.setGuest(booking.getGuest());
		request.setType(type);
		request.setDescription("Solicitud " + type);
		request.setStatus(ServiceRequestStatus.pending);
		request.setRequestedAt(now());
		request.setCreatedAt(now());
		request.setUpdatedAt(now());
		request = serviceRequestRepository.save(request);
		requestIds.add(request.getId());
		return request.getId();
	}

	private User createResponsibleUser() {
		Role role = new Role();
		role.setCode("concierge_resp_" + uniqueSuffix());
		role.setName("Concierge responsible");
		role.setCreatedAt(now());
		role.setUpdatedAt(now());
		role = roleRepository.save(role);
		roleIds.add(role.getId());

		User user = new User();
		user.setFirstName("Concierge");
		user.setLastName("Responsible");
		user.setEmail("concierge.responsible.%s@aurora.test".formatted(uniqueSuffix()));
		user.setPasswordHash("not-used");
		user.setRole(role);
		user.setStatus(UserStatus.active);
		user.setCreatedAt(now());
		user.setUpdatedAt(now());
		user = userRepository.save(user);
		userIds.add(user.getId());
		return user;
	}

	private static String createBody(UUID bookingId, String description) {
		return """
				{"bookingId": "%s", "description": "%s"}
				""".formatted(bookingId, description);
	}

	private static String statusBody(String status) {
		return """
				{"status": "%s"}
				""".formatted(status);
	}
}
