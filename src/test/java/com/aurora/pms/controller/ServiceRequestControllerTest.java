package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;
import com.aurora.pms.repository.ServiceRequestRepository;
import com.aurora.pms.security.SecurityPermissions;
import com.jayway.jsonpath.JsonPath;

class ServiceRequestControllerTest extends AbstractCatalogApiTest {

	private static final String BASE_PATH = "/api/v1/service-requests";

	@Autowired
	private ServiceRequestRepository serviceRequestRepository;

	private final List<UUID> requestIds = new ArrayList<>();

	@AfterEach
	void cleanUpServiceRequests() {
		serviceRequestRepository.deleteAllById(requestIds);
	}

	@Test
	void createMaintenanceRequestWithoutBookingPersistsRoomIssue() throws Exception {
		Room room = createRoom(createRoomType());

		MvcResult result = mockMvc.perform(post(BASE_PATH)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomId": "%s", "type": "maintenance",
								 "description": "  AC not cooling  ", "notes": "Reported by front desk"}
								""".formatted(room.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.bookingId").value(nullValue()))
				.andExpect(jsonPath("$.roomId").value(room.getId().toString()))
				.andExpect(jsonPath("$.type").value("maintenance"))
				.andExpect(jsonPath("$.status").value("pending"))
				.andExpect(jsonPath("$.description").value("AC not cooling"))
				.andReturn();

		UUID id = trackRequest(result);
		assertThat(serviceRequestRepository.findById(id)).get()
				.satisfies(stored -> {
					assertThat(stored.getBooking()).isNull();
					assertThat(stored.getType()).isEqualTo(ServiceRequestType.maintenance);
				});
	}

	@Test
	void createOtherRequestWithBookingUsesBookingGuest() throws Exception {
		Booking booking = createServiceBooking();

		MvcResult result = mockMvc.perform(post(BASE_PATH)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookingId": "%s", "roomId": "%s", "type": "other",
								 "description": "Safe battery is low"}
								""".formatted(booking.getId(), booking.getRoom().getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bookingId").value(booking.getId().toString()))
				.andExpect(jsonPath("$.guestId").value(booking.getGuest().getId().toString()))
				.andReturn();

		trackRequest(result);
	}

	@Test
	void dedicatedRequestTypesAreRejected() throws Exception {
		Room room = createRoom(createRoomType());

		for (String type : List.of("concierge", "housekeeping")) {
			mockMvc.perform(post(BASE_PATH)
							.with(staffUser())
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"roomId": "%s", "type": "%s", "description": "Use dedicated endpoint"}
									""".formatted(room.getId(), type)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("Use the dedicated endpoint for " + type + " requests"));
		}
	}

	@Test
	void listAndDetailReturnOperationalRequests() throws Exception {
		Booking booking = createServiceBooking();
		String maintenance = createRequest(booking, "maintenance", "Leak under sink");
		createRequest(booking, "other", "Extra desk lamp");

		mockMvc.perform(get(BASE_PATH)
						.param("type", "maintenance")
						.param("bookingId", booking.getId().toString())
						.with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(maintenance));

		mockMvc.perform(get(BASE_PATH + "/{id}", maintenance).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value("Leak under sink"));
	}

	@Test
	void housekeepingOnlyListsHousekeepingAndMaintenanceRequests() throws Exception {
		Booking booking = createServiceBooking();
		UUID housekeeping = persistRequest(booking, ServiceRequestType.housekeeping, "Stayover cleanup");
		UUID maintenance = persistRequest(booking, ServiceRequestType.maintenance, "Loose faucet");
		UUID concierge = persistRequest(booking, ServiceRequestType.concierge, "Dinner booking");
		UUID other = persistRequest(booking, ServiceRequestType.other, "Operational note");

		mockMvc.perform(get(BASE_PATH).with(roleUser("ROLE_HOUSEKEEPING", SecurityPermissions.SERVICE_REQUESTS_READ)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(housekeeping.toString())))
				.andExpect(jsonPath("$[*].id", hasItem(maintenance.toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(concierge.toString()))))
				.andExpect(jsonPath("$[*].id", not(hasItem(other.toString()))));
	}

	@Test
	void detailOutsideRoleDomainReturnsForbidden() throws Exception {
		UUID maintenance = persistRequest(createServiceBooking(), ServiceRequestType.maintenance, "Fix light");

		mockMvc.perform(get(BASE_PATH + "/{id}", maintenance)
						.with(roleUser("ROLE_CONCIERGE", SecurityPermissions.SERVICE_REQUESTS_READ)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void roleFilterOutsideDomainReturnsForbidden() throws Exception {
		mockMvc.perform(get(BASE_PATH)
						.param("type", "concierge")
						.with(roleUser("ROLE_HOUSEKEEPING", SecurityPermissions.SERVICE_REQUESTS_READ)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void receptionCannotChangeSpecializedRequestById() throws Exception {
		UUID housekeeping = persistRequest(createServiceBooking(), ServiceRequestType.housekeeping, "Refresh towels");

		mockMvc.perform(post(BASE_PATH + "/{id}/status", housekeeping)
						.with(roleUser("ROLE_RECEPTION", SecurityPermissions.SERVICE_REQUESTS_WRITE))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "accepted"}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void roomServiceCannotUseServiceRequestsEvenWithPermission() throws Exception {
		mockMvc.perform(get(BASE_PATH)
						.with(roleUser("ROLE_ROOM_SERVICE", SecurityPermissions.SERVICE_REQUESTS_READ)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void statusFlowReachesCompleted() throws Exception {
		Booking booking = createServiceBooking();
		String id = createRequest(booking, "maintenance", "Fix shower");

		changeStatus(id, "accepted");
		mockMvc.perform(post(BASE_PATH + "/{id}/status", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "in_progress", "notes": "Tech started"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("in_progress"))
				.andExpect(jsonPath("$.startedAt").exists());
		mockMvc.perform(post(BASE_PATH + "/{id}/status", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "completed", "notes": "Done"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("completed"))
				.andExpect(jsonPath("$.completedAt").exists())
				.andExpect(jsonPath("$.notes").value("Tech started\nDone"));

		assertThat(serviceRequestRepository.findById(UUID.fromString(id))).get()
				.extracting(ServiceRequest::getStatus).isEqualTo(ServiceRequestStatus.completed);
	}

	@Test
	void invalidTransitionReturnsBadRequest() throws Exception {
		String id = createRequest(createServiceBooking(), "maintenance", "Fix door");

		mockMvc.perform(post(BASE_PATH + "/{id}/status", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "completed"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid status transition from pending to completed"));
	}

	@Test
	void missingReferencesReturnBadRequestOrNotFoundByContext() throws Exception {
		Room room = createRoom(createRoomType());
		Booking booking = createServiceBooking();

		mockMvc.perform(post(BASE_PATH)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomId": "%s", "type": "maintenance", "description": "Missing room"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Room not found")));

		mockMvc.perform(post(BASE_PATH)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookingId": "%s", "roomId": "%s", "type": "maintenance", "description": "Bad room"}
								""".formatted(booking.getId(), room.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Room does not belong to booking: " + booking.getId()));

		mockMvc.perform(get(BASE_PATH + "/{id}", UUID.randomUUID()).with(staffUser()))
				.andExpect(status().isNotFound());
	}

	static Stream<Arguments> serviceRequestEndpoints() {
		String id = UUID.randomUUID().toString();
		return Stream.of(
				Arguments.of(HttpMethod.GET, BASE_PATH),
				Arguments.of(HttpMethod.GET, BASE_PATH + "/" + id),
				Arguments.of(HttpMethod.POST, BASE_PATH),
				Arguments.of(HttpMethod.POST, BASE_PATH + "/" + id + "/status")
		);
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("serviceRequestEndpoints")
	void endpointWithoutTokenReturnsUnauthorized(HttpMethod method, String path) throws Exception {
		mockMvc.perform(request(method, path)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	private Booking createServiceBooking() {
		RoomType roomType = createRoomType();
		return createBooking(createGuest(), roomType, createRoom(roomType), createRate(roomType));
	}

	private String createRequest(Booking booking, String type, String description) throws Exception {
		MvcResult result = mockMvc.perform(post(BASE_PATH)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookingId": "%s", "roomId": "%s", "type": "%s", "description": "%s"}
								""".formatted(booking.getId(), booking.getRoom().getId(), type, description)))
				.andExpect(status().isCreated())
				.andReturn();
		return trackRequest(result).toString();
	}

	private void changeStatus(String id, String status) throws Exception {
		mockMvc.perform(post(BASE_PATH + "/{id}/status", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "%s"}
								""".formatted(status)))
				.andExpect(status().isOk());
	}

	private UUID persistRequest(Booking booking, ServiceRequestType type, String description) {
		ServiceRequest request = new ServiceRequest();
		request.setBooking(booking);
		request.setRoom(booking.getRoom());
		request.setGuest(booking.getGuest());
		request.setType(type);
		request.setDescription(description);
		request.setStatus(ServiceRequestStatus.pending);
		request.setRequestedAt(now());
		request.setCreatedAt(now());
		request.setUpdatedAt(now());
		request = serviceRequestRepository.save(request);
		requestIds.add(request.getId());
		return request.getId();
	}

	private static RequestPostProcessor roleUser(String role, String permission) {
		return user(role.toLowerCase() + "@aurora.test")
				.authorities(
						new SimpleGrantedAuthority(role),
						new SimpleGrantedAuthority(permission)
				);
	}

	private UUID trackRequest(MvcResult result) throws Exception {
		UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
		requestIds.add(id);
		return id;
	}
}
