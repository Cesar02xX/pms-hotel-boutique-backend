package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Role;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.ServiceRequest;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.RoomStatus;
import com.aurora.pms.model.enums.UserStatus;
import com.aurora.pms.repository.RoleRepository;
import com.aurora.pms.repository.ServiceRequestRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.service.HousekeepingService;

class HousekeepingControllerTest extends AbstractCatalogApiTest {

	@Autowired
	private HousekeepingService housekeepingService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private ServiceRequestRepository serviceRequestRepository;

	private final List<UUID> userIds = new ArrayList<>();
	private final List<UUID> roleIds = new ArrayList<>();
	private final List<UUID> housekeepingRoomIds = new ArrayList<>();
	private final List<UUID> serviceRequestIds = new ArrayList<>();

	@BeforeEach
	void setUpHousekeepingActor() {
		userRepository.findByEmail("catalog.tester@aurora.test").ifPresent(user -> userRepository.deleteById(user.getId()));

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
		user.setEmail("catalog.tester@aurora.test");
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
		serviceRequestRepository.deleteAllById(serviceRequestIds);
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

		mockMvc.perform(get("/api/v1/housekeeping/rooms").with(staffUser()))
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
						.with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(matchingRoom.getId().toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(otherRoom.getId().toString()))));
	}

	@Test
	void getRoomReturnsOperationalDetail() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.cleaning);

		mockMvc.perform(get("/api/v1/housekeeping/rooms/{roomId}", room.getId()).with(staffUser()))
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
		mockMvc.perform(get("/api/v1/housekeeping/rooms/{roomId}", UUID.randomUUID()).with(staffUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void dirtyRoomCanStartCleaning() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.dirty);
		room.setStatus(RoomStatus.occupied);
		room = roomRepository.save(room);

		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/start", room.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housekeepingStatus").value("cleaning"))
				.andExpect(jsonPath("$.status").value("occupied"));

		Room saved = roomRepository.findById(room.getId()).orElseThrow();
		assertThat(saved.getHousekeepingStatus()).isEqualTo(RoomHousekeepingStatus.cleaning);
		assertThat(saved.getStatus()).isEqualTo(RoomStatus.occupied);
	}

	@Test
	void cleaningRoomCanCompleteCleaning() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.cleaning);

		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/complete", room.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housekeepingStatus").value("clean"));
	}

	@Test
	void cleanRoomCanBeInspected() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.clean);

		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/inspect", room.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housekeepingStatus").value("inspected"));
	}

	@Test
	void fullHousekeepingFlowIsAccepted() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.dirty);

		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/start", room.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housekeepingStatus").value("cleaning"));
		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/complete", room.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housekeepingStatus").value("clean"));
		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/inspect", room.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housekeepingStatus").value("inspected"));
	}

	@Test
	void invalidStateJumpReturnsBadRequest() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.dirty);

		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/complete", room.getId()).with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith("Cannot complete cleaning room")));
	}

	@Test
	void repeatedTransitionReturnsBadRequest() throws Exception {
		Room room = createRoomWithHousekeepingStatus(RoomHousekeepingStatus.dirty);

		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/start", room.getId()).with(staffUser()))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/housekeeping/rooms/{roomId}/start", room.getId()).with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith("Cannot start cleaning room")));
	}

	@Test
	void listStayoverCleaningsAllowsMissingBookingFilter() throws Exception {
		Booking booking = createCheckedInBooking();
		ServiceRequest stayover = createStayover(booking, "Limpieza de estancia");

		mockMvc.perform(get("/api/v1/housekeeping/rooms/stayover-cleanings").with(staffUser()))
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
				housekeepingService.startCleaning(room.getId(), "catalog.tester@aurora.test");
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
		mockMvc.perform(get("/api/v1/housekeeping/rooms/{roomId}", "RM-101").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void invalidHousekeepingStatusFilterReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/housekeeping/rooms")
						.param("housekeepingStatus", "ready")
						.with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	private Room createRoomWithHousekeepingStatus(RoomHousekeepingStatus housekeepingStatus) {
		Room room = createRoom(createRoomType());
		room.setHousekeepingStatus(housekeepingStatus);
		room = roomRepository.save(room);
		housekeepingRoomIds.add(room.getId());
		return room;
	}

	private Booking createCheckedInBooking() {
		RoomType roomType = createRoomType();
		Room room = createRoom(roomType);
		Guest guest = createGuest();
		Rate rate = createRate(roomType);
		Booking booking = createBooking(guest, roomType, room, rate);
		booking.setStatus(BookingStatus.checked_in);
		return bookingRepository.save(booking);
	}

	private ServiceRequest createStayover(Booking booking, String description) {
		ServiceRequest stayover = serviceRequestRepository.findById(housekeepingService
				.createStayoverCleaning(
						booking.getRoom().getId(),
						booking.getId(),
						description,
						"catalog.tester@aurora.test"
				)
				.id()).orElseThrow();
		serviceRequestIds.add(stayover.getId());
		return stayover;
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
