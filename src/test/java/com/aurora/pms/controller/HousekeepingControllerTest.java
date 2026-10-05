package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MvcResult;

import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.HousekeepingChecklist;
import com.aurora.pms.model.HousekeepingChecklistItem;
import com.aurora.pms.model.Role;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.HousekeepingChecklistStatus;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.RoomStatus;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;
import com.aurora.pms.model.enums.UserStatus;
import com.aurora.pms.repository.HousekeepingChecklistRepository;
import com.aurora.pms.repository.RoleRepository;
import com.aurora.pms.repository.ServiceRequestRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.security.SecurityPermissions;
import com.aurora.pms.service.HousekeepingService;
import com.jayway.jsonpath.JsonPath;

class HousekeepingControllerTest extends AbstractCatalogApiTest {

	private static final String STAYOVER_PATH = "/api/v1/housekeeping/rooms/stayover-cleanings";
	private static final String CHECKLIST_PATH = "/api/v1/housekeeping/checklists";

	@Autowired
	private HousekeepingService housekeepingService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private ServiceRequestRepository serviceRequestRepository;

	@Autowired
	private HousekeepingChecklistRepository housekeepingChecklistRepository;

	private final List<UUID> userIds = new ArrayList<>();
	private final List<UUID> roleIds = new ArrayList<>();
	private final List<UUID> housekeepingRoomIds = new ArrayList<>();
	private final List<UUID> serviceRequestIds = new ArrayList<>();
	private final List<UUID> checklistIds = new ArrayList<>();
	private String housekeepingActorEmail;

	@BeforeEach
	void setUpHousekeepingActor() {
		housekeepingActorEmail = "housekeeping.tester." + uniqueSuffix() + "@aurora.test";

		Role role = new Role();
		role.setCode("HOUSEKEEPING_TEST_" + uniqueSuffix());
		role.setName("Housekeeping Test");
		role.setCreatedAt(now());
		role.setUpdatedAt(now());
		role = roleRepository.save(role);
		roleIds.add(role.getId());

		User user = new User();
		user.setFirstName("Catalog");
		user.setLastName("Tester");
		user.setEmail(housekeepingActorEmail);
		user.setPasswordHash("hash");
		user.setRole(role);
		user.setStatus(UserStatus.active);
		user.setCreatedAt(now());
		user.setUpdatedAt(now());
		user = userRepository.save(user);
		userIds.add(user.getId());
	}

	@AfterEach
	void cleanUpHousekeepingActor() {
		for (UUID roomId : housekeepingRoomIds) {
			housekeepingChecklistRepository.search(roomId, null, null)
					.stream()
					.map(HousekeepingChecklist::getId)
					.filter(id -> !checklistIds.contains(id))
					.forEach(checklistIds::add);
		}
		housekeepingChecklistRepository.deleteAllById(checklistIds);
		checklistIds.clear();
		serviceRequestRepository.deleteAllById(serviceRequestIds);
		serviceRequestIds.clear();
		roomRepository.deleteAllById(housekeepingRoomIds);
		userRepository.deleteAllById(userIds);
		roleRepository.deleteAllById(roleIds);
		housekeepingRoomIds.clear();
		serviceRequestIds.clear();
		userIds.clear();
		roleIds.clear();
	}

	@Test
	void listRoomsReturnsOkIncludingExistingRoom() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.dirty);

		mockMvc.perform(get("/api/v1/housekeeping/rooms").with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(room.getId().toString())))
				.andExpect(jsonPath("$[?(@.id == '%s')].housekeepingStatus".formatted(room.getId()))
						.value(hasItem("dirty")));
	}

	@ParameterizedTest
	@EnumSource(RoomHousekeepingStatus.class)
	void listRoomsFiltersByHousekeepingStatus(RoomHousekeepingStatus housekeepingStatus) throws Exception {
		Room matchingRoom = createRoomWithHousekeepingStatus(housekeepingStatus);
		Room otherRoom = createRoomWithHousekeepingStatus(nextStatus(housekeepingStatus));

		mockMvc.perform(get("/api/v1/housekeeping/rooms")
						.param("housekeepingStatus", housekeepingStatus.name())
						.with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(matchingRoom.getId().toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(otherRoom.getId().toString()))));
	}

	@Test
	void getRoomReturnsOperationalDetail() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.cleaning);

		mockMvc.perform(get("/api/v1/housekeeping/rooms/{roomId}", room.getId()).with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(room.getId().toString()))
				.andExpect(jsonPath("$.roomNumber").value(room.getRoomNumber()))
				.andExpect(jsonPath("$.roomTypeId").value(room.getRoomType().getId().toString()))
				.andExpect(jsonPath("$.status").value("available"))
				.andExpect(jsonPath("$.housekeepingStatus").value("cleaning"))
				.andExpect(jsonPath("$.updatedAt").exists());
	}

	@Test
	void missingRoomReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/housekeeping/rooms/{roomId}", UUID.randomUUID()).with(housekeepingUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void dirtyRoomCanStartCleaning() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.dirty);
		room.setStatus(RoomStatus.occupied);
		room = roomRepository.save(room);

		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/start", room.getId()).with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housekeepingStatus").value("cleaning"))
				.andExpect(jsonPath("$.status").value("occupied"));

		Room saved = roomRepository.findById(room.getId()).orElseThrow();
		assertThat(saved.getHousekeepingStatus()).isEqualTo(RoomHousekeepingStatus.cleaning);
		assertThat(saved.getStatus()).isEqualTo(RoomStatus.occupied);

		HousekeepingChecklist checklist = activeTurnoverChecklist(room.getId());
		assertThat(checklist.getServiceRequest()).isNull();
		assertThat(checklist.getStatus()).isEqualTo(HousekeepingChecklistStatus.in_progress);
		assertThat(checklist.getItems()).extracting(HousekeepingChecklistItem::getLabel)
				.contains("Cama preparada", "Bano limpio", "Piso limpio");
	}

	@Test
	void cleaningRoomCanCompleteCleaning() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.cleaning);
		createTurnoverChecklist(room.getId());
		checkTurnoverChecklistItems(room.getId());

		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/complete", room.getId()).with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housekeepingStatus").value("clean"));

		HousekeepingChecklist checklist = completedTurnoverChecklist(room.getId());
		assertThat(checklist.getStatus()).isEqualTo(HousekeepingChecklistStatus.completed);
		assertThat(checklist.getCompletedByUser().getEmail()).isEqualTo(housekeepingActorEmail);
	}

	@Test
	void cleaningRoomCannotCompleteWithUncheckedTurnoverChecklist() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.cleaning);
		createTurnoverChecklist(room.getId());

		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/complete", room.getId()).with(housekeepingUser()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Cannot complete checklist with unchecked items"));
	}

	@Test
	void cleanRoomCanBeInspected() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.clean);
		createCompletedTurnoverChecklist(room.getId());

		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/inspect", room.getId()).with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housekeepingStatus").value("inspected"));
	}

	@Test
	void cleanRoomCannotBeInspectedWithoutCompletedTurnoverChecklist() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.clean);

		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/inspect", room.getId()).with(housekeepingUser()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(startsWith("Cannot inspect room without completed turnover checklist")));
	}

	@Test
	void fullHousekeepingFlowIsAccepted() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.dirty);

		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/start", room.getId()).with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housekeepingStatus").value("cleaning"));
		checkTurnoverChecklistItems(room.getId());
		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/complete", room.getId()).with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housekeepingStatus").value("clean"));
		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/inspect", room.getId()).with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housekeepingStatus").value("inspected"));
	}

	@Test
	void invalidStateJumpReturnsBadRequest() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.dirty);

		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/complete", room.getId()).with(housekeepingUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith("Cannot complete cleaning room")));
	}

	@Test
	void repeatedTransitionReturnsBadRequest() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.dirty);

		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/start", room.getId()).with(housekeepingUser()))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/start", room.getId()).with(housekeepingUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith("Cannot start cleaning room")));
	}

	@Test
	void listStayoverCleaningsAllowsMissingBookingFilter() throws Exception {
		Booking booking = createCheckedInBooking();
		ServiceRequest stayover = createStayover(booking, "Limpieza de estancia");

		mockMvc.perform(get("/api/v1/housekeeping/rooms/stayover-cleanings").with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(stayover.getId().toString())))
				.andExpect(jsonPath("$[?(@.id == '%s')].bookingId".formatted(stayover.getId()))
						.value(hasItem(booking.getId().toString())));
	}

	@Test
	void concurrentStartCleaningAllowsOnlyOneTransition() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.dirty);
		ExecutorService executorService = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Boolean> task = () -> {
			ready.countDown();
			assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
			try {
				housekeepingService.startCleaning(room.getId(), housekeepingActorEmail);
				return true;
			} catch (BadRequestException exception) {
				return false;
			}
		};

		Future<Boolean> first = executorService.submit(task);
		Future<Boolean> second = executorService.submit(task);
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		List<Boolean> results = List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
		executorService.shutdownNow();

		assertThat(results).containsExactlyInAnyOrder(true, false);
		assertThat(roomRepository.findById(room.getId()).orElseThrow().getHousekeepingStatus())
				.isEqualTo(RoomHousekeepingStatus.cleaning);
	}

	@Test
	void invalidUuidReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/housekeeping/rooms/{roomId}", "RM-101").with(housekeepingUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void invalidHousekeepingStatusFilterReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/housekeeping/rooms")
						.param("housekeepingStatus", "ready")
						.with(housekeepingUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void listStayoverCleaningsWithoutBookingReturnsOnlyHousekeepingTasks() throws Exception {
		Booking first = createCheckedInBooking();
		Booking second = createCheckedInBooking();
		UUID firstTask = createServiceRequest(first, ServiceRequestType.housekeeping, ServiceRequestStatus.pending);
		UUID secondTask = createServiceRequest(second, ServiceRequestType.housekeeping, ServiceRequestStatus.in_progress);
		UUID concierge = createServiceRequest(first, ServiceRequestType.concierge, ServiceRequestStatus.pending);

		mockMvc.perform(get(STAYOVER_PATH).with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(firstTask.toString())))
				.andExpect(jsonPath("$[*].id", hasItem(secondTask.toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(concierge.toString()))))
				.andExpect(jsonPath("$[?(@.id == '%s')].roomNumber".formatted(firstTask))
						.value(hasItem(first.getRoom().getRoomNumber())));
	}

	@Test
	void listStayoverCleaningsFiltersByStatus() throws Exception {
		Booking booking = createCheckedInBooking();
		UUID pending = createServiceRequest(booking, ServiceRequestType.housekeeping, ServiceRequestStatus.pending);
		UUID inProgress = createServiceRequest(booking, ServiceRequestType.housekeeping, ServiceRequestStatus.in_progress);

		mockMvc.perform(get(STAYOVER_PATH).param("status", "pending").with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(pending.toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(inProgress.toString()))))
				.andExpect(jsonPath("$[*].status", everyItem(is("pending"))));
	}

	@Test
	void listStayoverCleaningsKeepsBookingFilter() throws Exception {
		Booking booking = createCheckedInBooking();
		Booking other = createCheckedInBooking();
		UUID own = createServiceRequest(booking, ServiceRequestType.housekeeping, ServiceRequestStatus.pending);
		UUID completed = createServiceRequest(booking, ServiceRequestType.housekeeping, ServiceRequestStatus.completed);
		UUID foreign = createServiceRequest(other, ServiceRequestType.housekeeping, ServiceRequestStatus.pending);

		mockMvc.perform(get(STAYOVER_PATH).param("bookingId", booking.getId().toString()).with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[*].bookingId", everyItem(is(booking.getId().toString()))))
				.andExpect(jsonPath("$[*].id", not(hasItem(foreign.toString()))));

		mockMvc.perform(get(STAYOVER_PATH)
						.param("bookingId", booking.getId().toString())
						.param("status", "pending")
						.with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(own.toString()))
				.andExpect(jsonPath("$[*].id", not(hasItem(completed.toString()))));
	}

	@Test
	void listStayoverCleaningsForMissingBookingReturnsNotFound() throws Exception {
		mockMvc.perform(get(STAYOVER_PATH).param("bookingId", UUID.randomUUID().toString()).with(housekeepingUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void listStayoverCleaningsWithInvalidStatusReturnsBadRequest() throws Exception {
		mockMvc.perform(get(STAYOVER_PATH).param("status", "done").with(housekeepingUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void createChecklistPersistsItemsAndResponsibleUser() throws Exception {
		Booking booking = createCheckedInBooking();
		UUID requestId = createServiceRequest(booking, ServiceRequestType.housekeeping, ServiceRequestStatus.pending);

		MvcResult result = mockMvc.perform(post(CHECKLIST_PATH)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "serviceRequestId": "%s",
								  "observations": "Primera pasada",
								  "items": [
								    {"label": "Cambiar sabanas", "checked": true, "notes": "Listo"},
								    {"label": "Reponer toallas"}
								  ]
								}
								""".formatted(requestId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.serviceRequestId").value(requestId.toString()))
				.andExpect(jsonPath("$.roomId").value(booking.getRoom().getId().toString()))
				.andExpect(jsonPath("$.status").value("pending"))
				.andExpect(jsonPath("$.responsibleUserEmail").value(housekeepingActorEmail))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].checked").value(true))
				.andExpect(jsonPath("$.items[0].checkedByUserEmail").value(housekeepingActorEmail))
				.andReturn();

		UUID checklistId = trackChecklist(result);
		HousekeepingChecklist saved = housekeepingChecklistRepository.findDetailedById(checklistId).orElseThrow();
		assertThat(saved.getItems()).hasSize(2);
		assertThat(saved.getResponsibleUser().getEmail()).isEqualTo(housekeepingActorEmail);
	}

	@Test
	void createChecklistForSameCleaningTaskReturnsConflict() throws Exception {
		Booking booking = createCheckedInBooking();
		UUID requestId = createServiceRequest(booking, ServiceRequestType.housekeeping, ServiceRequestStatus.pending);
		createChecklist(requestId);

		mockMvc.perform(post(CHECKLIST_PATH)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "serviceRequestId": "%s",
								  "items": [{"label": "Revisar minibar"}]
								}
								""".formatted(requestId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void createTurnoverChecklistPersistsWithoutServiceRequest() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.cleaning);

		MvcResult result = mockMvc.perform(post(CHECKLIST_PATH)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "roomId": "%s",
								  "observations": "Turnover salida 202",
								  "items": [
								    {"label": "Cama preparada"},
								    {"label": "Bano limpio", "checked": true}
								  ]
								}
								""".formatted(room.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.serviceRequestId").value(nullValue()))
				.andExpect(jsonPath("$.roomId").value(room.getId().toString()))
				.andExpect(jsonPath("$.status").value("pending"))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andReturn();

		UUID checklistId = trackChecklist(result);
		HousekeepingChecklist saved = housekeepingChecklistRepository.findDetailedById(checklistId).orElseThrow();
		assertThat(saved.getServiceRequest()).isNull();
		assertThat(saved.getRoom().getId()).isEqualTo(room.getId());
	}

	@Test
	void createTurnoverChecklistForSameActiveRoomReturnsConflict() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.cleaning);
		createTurnoverChecklist(room.getId());

		mockMvc.perform(post(CHECKLIST_PATH)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "roomId": "%s",
								  "items": [{"label": "Revisar bano"}]
								}
								""".formatted(room.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void createChecklistWithRoomAndServiceRequestReturnsBadRequest() throws Exception {
		Booking booking = createCheckedInBooking();
		UUID requestId = createServiceRequest(booking, ServiceRequestType.housekeeping, ServiceRequestStatus.pending);

		mockMvc.perform(post(CHECKLIST_PATH)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "serviceRequestId": "%s",
								  "roomId": "%s",
								  "items": [{"label": "Revisar bano"}]
								}
								""".formatted(requestId, booking.getRoom().getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void createChecklistCanCompleteWhenAllItemsAreChecked() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.cleaning);

		MvcResult result = mockMvc.perform(post(CHECKLIST_PATH)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "roomId": "%s",
								  "status": "completed",
								  "items": [
								    {"label": "Cama preparada", "checked": true},
								    {"label": "Bano limpio", "checked": true}
								  ]
								}
								""".formatted(room.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("completed"))
				.andExpect(jsonPath("$.completedByUserEmail").value(housekeepingActorEmail))
				.andReturn();

		UUID checklistId = trackChecklist(result);
		HousekeepingChecklist saved = housekeepingChecklistRepository.findDetailedById(checklistId).orElseThrow();
		assertThat(saved.getStatus()).isEqualTo(HousekeepingChecklistStatus.completed);
		assertThat(saved.getItems()).allMatch(HousekeepingChecklistItem::isChecked);
	}

	@Test
	void listChecklistsFiltersByRoomStatusAndResponsible() throws Exception {
		Booking ownBooking = createCheckedInBooking();
		Booking otherBooking = createCheckedInBooking();
		UUID ownRequest = createServiceRequest(ownBooking, ServiceRequestType.housekeeping, ServiceRequestStatus.pending);
		UUID otherRequest = createServiceRequest(otherBooking, ServiceRequestType.housekeeping, ServiceRequestStatus.pending);
		UUID ownChecklist = createChecklist(ownRequest);
		UUID otherChecklist = createChecklist(otherRequest);
		UUID responsibleId = userRepository.findByEmail(housekeepingActorEmail).orElseThrow().getId();

		mockMvc.perform(put(CHECKLIST_PATH + "/{id}", ownChecklist)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "in_progress", "items": [{"label": "Cambiar sabanas", "checked": true}]}
								"""))
				.andExpect(status().isOk());

		mockMvc.perform(get(CHECKLIST_PATH)
						.param("roomId", ownBooking.getRoom().getId().toString())
						.param("status", HousekeepingChecklistStatus.in_progress.name())
						.param("responsibleUserId", responsibleId.toString())
						.with(housekeepingUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(ownChecklist.toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(otherChecklist.toString()))));
	}

	@Test
	void updateChecklistCanCompleteCheckedItemsWithoutDroppingHistory() throws Exception {
		Booking booking = createCheckedInBooking();
		UUID requestId = createServiceRequest(booking, ServiceRequestType.housekeeping, ServiceRequestStatus.pending);
		UUID checklistId = createChecklist(requestId);
		HousekeepingChecklist checklist = housekeepingChecklistRepository.findDetailedById(checklistId).orElseThrow();
		UUID firstItemId = checklist.getItems().get(0).getId();

		mockMvc.perform(put(CHECKLIST_PATH + "/{id}", checklistId)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "status": "completed",
								  "observations": "Habitacion lista",
								  "items": [
								    {"id": "%s", "label": "Cambiar sabanas", "checked": true, "notes": "OK"},
								    {"label": "Desinfectar bano", "checked": true}
								  ]
								}
								""".formatted(firstItemId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("completed"))
				.andExpect(jsonPath("$.observations").value("Habitacion lista"))
				.andExpect(jsonPath("$.completedByUserEmail").value(housekeepingActorEmail))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[*].id", hasItem(firstItemId.toString())));

		HousekeepingChecklist saved = housekeepingChecklistRepository.findDetailedById(checklistId).orElseThrow();
		assertThat(saved.getItems()).extracting(item -> item.getId()).contains(firstItemId);
		assertThat(saved.getStatus()).isEqualTo(HousekeepingChecklistStatus.completed);
	}

	@Test
	void completeChecklistWithUncheckedItemsReturnsConflict() throws Exception {
		Booking booking = createCheckedInBooking();
		UUID requestId = createServiceRequest(booking, ServiceRequestType.housekeeping, ServiceRequestStatus.pending);
		UUID checklistId = createChecklist(requestId);

		mockMvc.perform(put(CHECKLIST_PATH + "/{id}", checklistId)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\": \"completed\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void completedChecklistCannotBeUpdatedWithUncheckedItem() throws Exception {
		Booking booking = createCheckedInBooking();
		UUID requestId = createServiceRequest(booking, ServiceRequestType.housekeeping, ServiceRequestStatus.pending);
		UUID checklistId = createChecklist(requestId);
		HousekeepingChecklist checklist = housekeepingChecklistRepository.findDetailedById(checklistId).orElseThrow();
		UUID firstItemId = checklist.getItems().get(0).getId();
		completeChecklist(checklistId, firstItemId);

		mockMvc.perform(put(CHECKLIST_PATH + "/{id}", checklistId)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "items": [
								    {"id": "%s", "label": "Cambiar sabanas", "checked": false}
								  ]
								}
								""".formatted(firstItemId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));

		HousekeepingChecklist saved = housekeepingChecklistRepository.findDetailedById(checklistId).orElseThrow();
		assertThat(saved.getStatus()).isEqualTo(HousekeepingChecklistStatus.completed);
		assertThat(saved.getItems()).allMatch(HousekeepingChecklistItem::isChecked);
	}

	@Test
	void updateCompletedChecklistReturnsConflict() throws Exception {
		Booking booking = createCheckedInBooking();
		UUID requestId = createServiceRequest(booking, ServiceRequestType.housekeeping, ServiceRequestStatus.pending);
		UUID checklistId = createChecklist(requestId);
		HousekeepingChecklist checklist = housekeepingChecklistRepository.findDetailedById(checklistId).orElseThrow();
		completeChecklist(checklistId, checklist.getItems().get(0).getId());

		mockMvc.perform(put(CHECKLIST_PATH + "/{id}", checklistId)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"observations\": \"Cambio tardio\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void updateCancelledChecklistReturnsConflict() throws Exception {
		Booking booking = createCheckedInBooking();
		UUID requestId = createServiceRequest(booking, ServiceRequestType.housekeeping, ServiceRequestStatus.pending);
		UUID checklistId = createChecklist(requestId);

		mockMvc.perform(put(CHECKLIST_PATH + "/{id}", checklistId)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\": \"cancelled\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(put(CHECKLIST_PATH + "/{id}", checklistId)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"observations\": \"Cambio tardio\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void updateMissingChecklistReturnsNotFound() throws Exception {
		mockMvc.perform(put(CHECKLIST_PATH + "/{id}", UUID.randomUUID())
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\": \"in_progress\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	/** Mismos permisos que el rol housekeeping sembrado: sin bookings.read. */
	private RequestPostProcessor housekeepingUser() {
		return userWithPermissions(
				housekeepingActorEmail,
				SecurityPermissions.HOUSEKEEPING_READ,
				SecurityPermissions.HOUSEKEEPING_WRITE,
				SecurityPermissions.ROOMS_READ
		);
	}

	private Booking createCheckedInBooking() {
		RoomType roomType = createRoomType();
		Booking booking = createBooking(createGuest(), roomType, createRoom(roomType), createRate(roomType));
		booking.setStatus(BookingStatus.checked_in);
		bookingRepository.save(booking);
		return booking;
	}

	private UUID createServiceRequest(Booking booking, ServiceRequestType type, ServiceRequestStatus status) {
		ServiceRequest request = new ServiceRequest();
		request.setBooking(booking);
		request.setRoom(booking.getRoom());
		request.setGuest(booking.getGuest());
		request.setType(type);
		request.setDescription("Solicitud " + type);
		request.setStatus(status);
		request.setRequestedAt(now());
		request.setCreatedAt(now());
		request.setUpdatedAt(now());
		request = serviceRequestRepository.save(request);
		serviceRequestIds.add(request.getId());
		return request.getId();
	}

	private Room createRoomWithHousekeepingStatus(RoomHousekeepingStatus housekeepingStatus) {
		Room room = createRoom(createRoomType());
		room.setHousekeepingStatus(housekeepingStatus);
		room = roomRepository.save(room);
		housekeepingRoomIds.add(room.getId());
		return room;
	}

	private ServiceRequest createStayover(Booking booking, String description) {
		ServiceRequest stayover = serviceRequestRepository.findById(housekeepingService
				.createStayoverCleaning(
						booking.getRoom().getId(),
						booking.getId(),
						description,
						housekeepingActorEmail
				)
				.id()).orElseThrow();
		serviceRequestIds.add(stayover.getId());
		return stayover;
	}

	private UUID createChecklist(UUID serviceRequestId) throws Exception {
		MvcResult result = mockMvc.perform(post(CHECKLIST_PATH)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "serviceRequestId": "%s",
								  "items": [{"label": "Cambiar sabanas"}]
								}
								""".formatted(serviceRequestId)))
				.andExpect(status().isCreated())
				.andReturn();
		return trackChecklist(result);
	}

	private UUID createTurnoverChecklist(UUID roomId) throws Exception {
		MvcResult result = mockMvc.perform(post(CHECKLIST_PATH)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "roomId": "%s",
								  "items": [{"label": "Cambiar sabanas"}]
								}
								""".formatted(roomId)))
				.andExpect(status().isCreated())
				.andReturn();
		return trackChecklist(result);
	}

	private UUID createCompletedTurnoverChecklist(UUID roomId) throws Exception {
		MvcResult result = mockMvc.perform(post(CHECKLIST_PATH)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "roomId": "%s",
								  "status": "completed",
								  "items": [
								    {"label": "Cambiar sabanas", "checked": true},
								    {"label": "Bano limpio", "checked": true}
								  ]
								}
								""".formatted(roomId)))
				.andExpect(status().isCreated())
				.andReturn();
		return trackChecklist(result);
	}

	private HousekeepingChecklist activeTurnoverChecklist(UUID roomId) {
		return housekeepingChecklistRepository.search(roomId, null, null)
				.stream()
				.filter(checklist -> checklist.getServiceRequest() == null)
				.filter(checklist -> checklist.getStatus() == HousekeepingChecklistStatus.pending
						|| checklist.getStatus() == HousekeepingChecklistStatus.in_progress)
				.findFirst()
				.orElseThrow();
	}

	private HousekeepingChecklist completedTurnoverChecklist(UUID roomId) {
		return housekeepingChecklistRepository.search(roomId, HousekeepingChecklistStatus.completed, null)
				.stream()
				.filter(checklist -> checklist.getServiceRequest() == null)
				.findFirst()
				.orElseThrow();
	}

	private void checkTurnoverChecklistItems(UUID roomId) {
		HousekeepingChecklist checklist = activeTurnoverChecklist(roomId);
		User actor = userRepository.findByEmail(housekeepingActorEmail).orElseThrow();
		checklist.getItems().forEach(item -> {
			item.setChecked(true);
			item.setCheckedAt(now());
			item.setCheckedByUser(actor);
			item.setUpdatedAt(now());
		});
		housekeepingChecklistRepository.save(checklist);
	}

	private UUID trackChecklist(MvcResult result) throws Exception {
		UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
		checklistIds.add(id);
		return id;
	}

	private void completeChecklist(UUID checklistId, UUID itemId) throws Exception {
		mockMvc.perform(put(CHECKLIST_PATH + "/{id}", checklistId)
						.with(housekeepingUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "status": "completed",
								  "items": [
								    {"id": "%s", "label": "Cambiar sabanas", "checked": true}
								  ]
								}
								""".formatted(itemId)))
				.andExpect(status().isOk());
	}

	private static RoomHousekeepingStatus nextStatus(RoomHousekeepingStatus housekeepingStatus) {
		return switch (housekeepingStatus) {
			case dirty -> RoomHousekeepingStatus.cleaning;
			case cleaning -> RoomHousekeepingStatus.clean;
			case clean -> RoomHousekeepingStatus.inspected;
			case inspected -> RoomHousekeepingStatus.dirty;
		};
	}
}
