package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.aurora.pms.model.RoomFeature;
import com.aurora.pms.model.RoomType;
import com.jayway.jsonpath.JsonPath;

class RoomTypeControllerTest extends AbstractCatalogApiTest {

	@Test
	void listRoomTypesReturnsOkWithFeatureIds() throws Exception {
		RoomFeature balcony = createRoomFeature("Balcony");
		MvcResult created = createRoomTypeViaApi("[\"%s\"]".formatted(balcony.getId()));
		UUID roomTypeId = trackCreatedRoomType(created);

		mockMvc.perform(get("/api/v1/room-types").with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(roomTypeId.toString())))
				.andExpect(jsonPath("$[?(@.id == '%s')].roomFeatureIds[0]".formatted(roomTypeId))
						.value(balcony.getId().toString()));
	}

	@Test
	void getRoomTypeByIdReturnsRoomType() throws Exception {
		RoomType roomType = createRoomType();

		mockMvc.perform(get("/api/v1/room-types/{id}", roomType.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(roomType.getId().toString()))
				.andExpect(jsonPath("$.code").value(roomType.getCode()))
				.andExpect(jsonPath("$.capacity").value(2))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.roomFeatureIds", hasSize(0)));
	}

	@Test
	void getMissingRoomTypeReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/room-types/{id}", UUID.randomUUID()).with(staffUser()))
				.andExpect(status().isNotFound());
	}

	@Test
	void createRoomTypeReturnsCreatedAndStoresFeaturesWithoutDuplicates() throws Exception {
		RoomFeature airConditioning = createRoomFeature("Air conditioning");
		RoomFeature balcony = createRoomFeature("Balcony");

		MvcResult result = createRoomTypeViaApi("[\"%s\", \"%s\", \"%s\"]".formatted(
				airConditioning.getId(), balcony.getId(), airConditioning.getId()));
		UUID roomTypeId = trackCreatedRoomType(result);

		assertThat(result.getResponse().getStatus()).isEqualTo(201);
		String body = result.getResponse().getContentAsString();
		assertThat(JsonPath.<List<String>>read(body, "$.roomFeatureIds"))
				.containsExactly(airConditioning.getId().toString(), balcony.getId().toString());
		assertThat(JsonPath.<Boolean>read(body, "$.active")).isTrue();
		assertThat(roomTypeFeatureRepository.findByIdRoomTypeId(roomTypeId)).hasSize(2);
	}

	@Test
	void createRoomTypeWithInvalidCapacityReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/room-types")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "T-%s", "name": "Invalid", "capacity": 0}
								""".formatted(uniqueSuffix())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.capacity").exists());
	}

	@Test
	void createRoomTypeWithMissingFieldsReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/room-types")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.code").exists())
				.andExpect(jsonPath("$.errors.name").exists())
				.andExpect(jsonPath("$.errors.capacity").exists());
	}

	@Test
	void createRoomTypeWithUnknownFeatureReturnsBadRequest() throws Exception {
		UUID unknownFeatureId = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/room-types")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "T-%s", "name": "Unknown feature", "capacity": 2, "roomFeatureIds": ["%s"]}
								""".formatted(uniqueSuffix(), unknownFeatureId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Room features not found: [" + unknownFeatureId + "]"));
	}

	@Test
	void createRoomTypeWithDuplicateCodeReturnsBadRequest() throws Exception {
		RoomType existing = createRoomType();

		mockMvc.perform(post("/api/v1/room-types")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "%s", "name": "Duplicate", "capacity": 2}
								""".formatted(existing.getCode())))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updateRoomTypeReplacesFeaturesAndKeepsCreatedAt() throws Exception {
		RoomFeature airConditioning = createRoomFeature("Air conditioning");
		RoomFeature balcony = createRoomFeature("Balcony");
		RoomFeature jacuzzi = createRoomFeature("Jacuzzi");
		MvcResult created = createRoomTypeViaApi("[\"%s\", \"%s\"]".formatted(
				airConditioning.getId(), balcony.getId()));
		UUID roomTypeId = trackCreatedRoomType(created);
		String before = created.getResponse().getContentAsString();

		mockMvc.perform(put("/api/v1/room-types/{id}", roomTypeId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Deluxe", "capacity": 3, "roomFeatureIds": ["%s", "%s"]}
								""".formatted(balcony.getId(), jacuzzi.getId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Deluxe"))
				.andExpect(jsonPath("$.capacity").value(3))
				.andExpect(jsonPath("$.code").value((String) JsonPath.read(before, "$.code")))
				.andExpect(jsonPath("$.roomFeatureIds", containsInAnyOrder(
						balcony.getId().toString(), jacuzzi.getId().toString())))
				.andExpect(jsonPath("$.updatedAt").value(not((String) JsonPath.read(before, "$.updatedAt"))));

		assertThat(roomTypeFeatureRepository.findByIdRoomTypeId(roomTypeId)).hasSize(2);

		// createdAt se compara contra lo persistido, que es lo que devuelve un GET.
		String persisted = mockMvc.perform(get("/api/v1/room-types/{id}", roomTypeId).with(staffUser()))
				.andReturn().getResponse().getContentAsString();
		assertThat(instantOf(JsonPath.read(persisted, "$.createdAt")))
				.isCloseTo(instantOf(JsonPath.read(before, "$.createdAt")), within(1, ChronoUnit.MILLIS));
	}

	@Test
	void updateRoomTypeWithoutFeatureIdsKeepsFeatures() throws Exception {
		RoomFeature balcony = createRoomFeature("Balcony");
		UUID roomTypeId = trackCreatedRoomType(createRoomTypeViaApi("[\"%s\"]".formatted(balcony.getId())));

		mockMvc.perform(put("/api/v1/room-types/{id}", roomTypeId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\": false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false))
				.andExpect(jsonPath("$.roomFeatureIds[0]").value(balcony.getId().toString()));
	}

	@Test
	void updateRoomTypeWithEmptyFeatureIdsRemovesFeatures() throws Exception {
		RoomFeature balcony = createRoomFeature("Balcony");
		UUID roomTypeId = trackCreatedRoomType(createRoomTypeViaApi("[\"%s\"]".formatted(balcony.getId())));

		mockMvc.perform(put("/api/v1/room-types/{id}", roomTypeId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"roomFeatureIds\": []}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roomFeatureIds", hasSize(0)));

		assertThat(roomTypeFeatureRepository.findByIdRoomTypeId(roomTypeId)).isEmpty();
	}

	@Test
	void updateRoomTypeWithInvalidCapacityReturnsBadRequest() throws Exception {
		RoomType roomType = createRoomType();

		mockMvc.perform(put("/api/v1/room-types/{id}", roomType.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"capacity\": -1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.capacity").exists());
	}

	@Test
	void updateMissingRoomTypeReturnsNotFound() throws Exception {
		mockMvc.perform(put("/api/v1/room-types/{id}", UUID.randomUUID())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"Missing\"}"))
				.andExpect(status().isNotFound());
	}

	private MvcResult createRoomTypeViaApi(String roomFeatureIdsJson) throws Exception {
		return mockMvc.perform(post("/api/v1/room-types")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"code": "T-%s", "name": "Standard", "description": "Test", "capacity": 2,
								 "bedConfiguration": "1 queen bed", "roomFeatureIds": %s}
								""".formatted(uniqueSuffix(), roomFeatureIdsJson)))
				.andExpect(status().isCreated())
				.andReturn();
	}

	/** Compara timestamps por instante (precisión de PostgreSQL), sin depender del offset serializado. */
	private static Instant instantOf(String value) {
		return OffsetDateTime.parse(value).toInstant().truncatedTo(ChronoUnit.MICROS);
	}
}
