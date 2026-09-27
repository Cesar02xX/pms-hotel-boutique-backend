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

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.jayway.jsonpath.JsonPath;

class RoomControllerTest extends AbstractCatalogApiTest {

	@Test
	void listRoomsReturnsOkIncludingExistingRoom() throws Exception {
		Room room = createRoom(createRoomType());

		mockMvc.perform(get("/api/v1/rooms").with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(room.getId().toString())));
	}

	@Test
	void getRoomByIdReturnsRoom() throws Exception {
		RoomType roomType = createRoomType();
		Room room = createRoom(roomType);

		mockMvc.perform(get("/api/v1/rooms/{id}", room.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(room.getId().toString()))
				.andExpect(jsonPath("$.roomNumber").value(room.getRoomNumber()))
				.andExpect(jsonPath("$.roomTypeId").value(roomType.getId().toString()))
				.andExpect(jsonPath("$.status").value("available"))
				.andExpect(jsonPath("$.housekeepingStatus").value("clean"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.updatedAt").exists());
	}

	@Test
	void getMissingRoomReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/rooms/{id}", UUID.randomUUID()).with(staffUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void getRoomWithInvalidUuidReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/rooms/{id}", "RM-101").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void createRoomReturnsCreatedWithDefaults() throws Exception {
		RoomType roomType = createRoomType();
		String roomNumber = "T" + uniqueSuffix();

		MvcResult result = mockMvc.perform(post("/api/v1/rooms")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomNumber": "%s", "roomTypeId": "%s", "floor": 3}
								""".formatted(roomNumber, roomType.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.roomNumber").value(roomNumber))
				.andExpect(jsonPath("$.roomTypeId").value(roomType.getId().toString()))
				.andExpect(jsonPath("$.floor").value(3))
				.andExpect(jsonPath("$.status").value("available"))
				.andExpect(jsonPath("$.housekeepingStatus").value("dirty"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.updatedAt").exists())
				.andReturn();

		UUID id = trackCreatedRoom(result);
		assertThat(roomRepository.findById(id)).isPresent();
	}

	@Test
	void createRoomIgnoresClientIdAndTimestamps() throws Exception {
		RoomType roomType = createRoomType();
		UUID clientId = UUID.randomUUID();

		MvcResult result = mockMvc.perform(post("/api/v1/rooms")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"id": "%s", "roomNumber": "T%s", "roomTypeId": "%s",
								 "createdAt": "2000-01-01T00:00:00Z", "updatedAt": "2000-01-01T00:00:00Z"}
								""".formatted(clientId, uniqueSuffix(), roomType.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(not(clientId.toString())))
				.andExpect(jsonPath("$.createdAt").value(not("2000-01-01T00:00:00Z")))
				.andReturn();

		trackCreatedRoom(result);
	}

	@Test
	void createRoomWithMissingFieldsReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/rooms")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"roomNumber\": \" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.roomNumber").exists())
				.andExpect(jsonPath("$.errors.roomTypeId").exists());
	}

	@Test
	void createRoomWithUnknownRoomTypeReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/rooms")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomNumber": "T%s", "roomTypeId": "%s"}
								""".formatted(uniqueSuffix(), UUID.randomUUID())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith("Room type not found")));
	}

	@Test
	void createRoomWithDuplicateRoomNumberReturnsBadRequest() throws Exception {
		RoomType roomType = createRoomType();
		Room existing = createRoom(roomType);

		mockMvc.perform(post("/api/v1/rooms")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomNumber": "%s", "roomTypeId": "%s"}
								""".formatted(existing.getRoomNumber(), roomType.getId())))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createRoomWithUnknownStatusReturnsBadRequest() throws Exception {
		RoomType roomType = createRoomType();

		mockMvc.perform(post("/api/v1/rooms")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomNumber": "T%s", "roomTypeId": "%s", "status": "closed"}
								""".formatted(uniqueSuffix(), roomType.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void updateRoomAppliesPartialChangesAndKeepsCreatedAt() throws Exception {
		RoomType roomType = createRoomType();
		RoomType otherRoomType = createRoomType();
		Room room = createRoom(roomType);

		String before = mockMvc.perform(get("/api/v1/rooms/{id}", room.getId()).with(staffUser()))
				.andReturn().getResponse().getContentAsString();

		mockMvc.perform(put("/api/v1/rooms/{id}", room.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomTypeId": "%s", "status": "maintenance", "notes": "Aire acondicionado en revisión"}
								""".formatted(otherRoomType.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roomNumber").value(room.getRoomNumber()))
				.andExpect(jsonPath("$.roomTypeId").value(otherRoomType.getId().toString()))
				.andExpect(jsonPath("$.status").value("maintenance"))
				.andExpect(jsonPath("$.housekeepingStatus").value("clean"))
				.andExpect(jsonPath("$.notes").value("Aire acondicionado en revisión"))
				.andExpect(jsonPath("$.createdAt").value((String) JsonPath.read(before, "$.createdAt")))
				.andExpect(jsonPath("$.updatedAt").value(not((String) JsonPath.read(before, "$.updatedAt"))));
	}

	@Test
	void updateMissingRoomReturnsNotFound() throws Exception {
		mockMvc.perform(put("/api/v1/rooms/{id}", UUID.randomUUID())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"floor\": 2}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void updateRoomWithUnknownRoomTypeReturnsBadRequest() throws Exception {
		Room room = createRoom(createRoomType());

		mockMvc.perform(put("/api/v1/rooms/{id}", room.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"roomTypeId\": \"%s\"}".formatted(UUID.randomUUID())))
				.andExpect(status().isBadRequest());
	}
}
