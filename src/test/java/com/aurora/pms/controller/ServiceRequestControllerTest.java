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
import com.aurora.pms.model.Role;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;
import com.aurora.pms.model.enums.UserStatus;
import com.aurora.pms.repository.RoleRepository;
import com.aurora.pms.repository.ServiceRequestRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.security.SecurityPermissions;
import com.jayway.jsonpath.JsonPath;

class ServiceRequestControllerTest extends AbstractCatalogApiTest {

	private static final String BASE_PATH = "/api/v1/service-requests";

	@Autowired
	private ServiceRequestRepository serviceRequestRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	private final List<UUID> requestIds = new ArrayList<>();
	private final List<UUID> userIds = new ArrayList<>();
	private final List<UUID> roleIds = new ArrayList<>();

	@AfterEach
	void cleanUpServiceRequests() {
		serviceRequestRepository.deleteAllById(requestIds);
		userRepository.deleteAllById(userIds);
		roleRepository.deleteAllById(roleIds);
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

		mockMvc.perform(get(BASE_PATH)
						.param("type", "maintenance")
						.param("roomId", room.getId().toString())
						.with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(id.toString())));
	}

	@Test
	void housekeepingReadCanListOperationalRequests() throws Exception {
		mockMvc.perform(get(BASE_PATH).with(user("housekeeping.reader@aurora.test")
					.authorities(new SimpleGrantedAuthority("ROLE_HOUSEKEEPING"),
							new SimpleGrantedAuthority(SecurityPermissions.HOUSEKEEPING_READ))))
				.andExpect(status().isOk());
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
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.status").value(403));
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
	void housekeepingCannotCreateOtherRequest() throws Exception {
		Room room = createRoom(createRoomType());

		mockMvc.perform(post(BASE_PATH)
						.with(roleUser("ROLE_HOUSEKEEPING", SecurityPermissions.SERVICE_REQUESTS_WRITE))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomId": "%s", "type": "other", "description": "Operational follow-up"}
								""".formatted(room.getId())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void housekeepingCannotChangeOtherRequestById() throws Exception {
		UUID other = persistRequest(createServiceBooking(), ServiceRequestType.other, "Operational follow-up");

		mockMvc.perform(post(BASE_PATH + "/{id}/status", other)
						.with(roleUser("ROLE_HOUSEKEEPING", SecurityPermissions.SERVICE_REQUESTS_WRITE))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "accepted"}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void housekeepingWritePermissionCanUpdateMaintenanceRequest() throws Exception {
		UUID maintenance = persistRequest(
				createServiceBooking(), ServiceRequestType.maintenance, "Review air conditioning");

		mockMvc.perform(post(BASE_PATH + "/{id}/status", maintenance)
						.with(roleUser("ROLE_HOUSEKEEPING", SecurityPermissions.HOUSEKEEPING_WRITE))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "accepted"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("accepted"));
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
		User actor = createStaffUser("executor");

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
						.with(housekeepingActor(actor))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "completed", "notes": "Done"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("completed"))
				.andExpect(jsonPath("$.completedAt").exists())
				.andExpect(jsonPath("$.completedByUserEmail").value(actor.getEmail()))
				.andExpect(jsonPath("$.notes").value("Tech started\nDone"));

		assertThat(serviceRequestRepository.findById(UUID.fromString(id))).get()
				.satisfies(request -> {
					assertThat(request.getStatus()).isEqualTo(ServiceRequestStatus.completed);
					assertThat(request.getCompletedByUser().getId()).isEqualTo(actor.getId());
				});
	}

	@Test
	void maintenanceCompletionRecordsExecutorSeparatelyFromAssignedResponsible() throws Exception {
		Booking booking = createServiceBooking();
		String id = createRequest(booking, "maintenance", "Fix shower");
		User assigned = createStaffUser("assigned");
		User actor = createStaffUser("executor");

		mockMvc.perform(post(BASE_PATH + "/{id}/status", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "accepted", "responsibleUserId": "%s"}
								""".formatted(assigned.getId())))
				.andExpect(status().isOk());
		mockMvc.perform(post(BASE_PATH + "/{id}/status", id)
						.with(housekeepingActor(actor))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "in_progress"}
								"""))
				.andExpect(status().isOk());
		mockMvc.perform(post(BASE_PATH + "/{id}/status", id)
						.with(housekeepingActor(actor))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "completed"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.responsibleUserEmail").value(assigned.getEmail()))
				.andExpect(jsonPath("$.completedByUserEmail").value(actor.getEmail()));

		assertThat(serviceRequestRepository.findById(UUID.fromString(id))).get()
				.satisfies(request -> {
					assertThat(request.getResponsibleUser().getId()).isEqualTo(assigned.getId());
					assertThat(request.getCompletedByUser().getId()).isEqualTo(actor.getId());
				});
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

	private User createStaffUser(String suffix) {
		Role role = new Role();
		role.setCode("housekeeping_test_" + uniqueSuffix());
		role.setName("Housekeeping Test");
		role.setCreatedAt(now());
		role.setUpdatedAt(now());
		role = roleRepository.save(role);
		roleIds.add(role.getId());

		User actor = new User();
		actor.setFirstName("Housekeeping");
		actor.setLastName(suffix);
		actor.setEmail("housekeeping.%s.%s@aurora.test".formatted(suffix, uniqueSuffix()));
		actor.setPasswordHash("not-used");
		actor.setRole(role);
		actor.setStatus(UserStatus.active);
		actor.setCreatedAt(now());
		actor.setUpdatedAt(now());
		actor = userRepository.save(actor);
		userIds.add(actor.getId());
		return actor;
	}

	private static RequestPostProcessor housekeepingActor(User actor) {
		return user(actor.getEmail()).authorities(
				new SimpleGrantedAuthority("ROLE_HOUSEKEEPING"),
				new SimpleGrantedAuthority(SecurityPermissions.SERVICE_REQUESTS_WRITE));
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
