package com.aurora.pms.controller;

import static org.hamcrest.Matchers.hasItem;
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

import com.aurora.pms.model.Rate;
import com.aurora.pms.model.RoomType;

class RateControllerTest extends AbstractCatalogApiTest {

	@Test
	void listRatesReturnsOkIncludingExistingRate() throws Exception {
		Rate rate = createRate(createRoomType());

		mockMvc.perform(get("/api/v1/rates").with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(rate.getId().toString())));
	}

	@Test
	void createRateReturnsCreatedWithDefaults() throws Exception {
		RoomType roomType = createRoomType();

		MvcResult result = mockMvc.perform(post("/api/v1/rates")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomTypeId": "%s", "name": "Temporada alta", "validFrom": "2026-12-01",
								 "validTo": "2027-01-15", "priceCents": 85000, "minimumNights": 2}
								""".formatted(roomType.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.roomTypeId").value(roomType.getId().toString()))
				.andExpect(jsonPath("$.validFrom").value("2026-12-01"))
				.andExpect(jsonPath("$.validTo").value("2027-01-15"))
				.andExpect(jsonPath("$.priceCents").value(85000))
				.andExpect(jsonPath("$.currency").value("GTQ"))
				.andExpect(jsonPath("$.minimumNights").value(2))
				.andExpect(jsonPath("$.refundable").value(true))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.createdAt").exists())
				.andReturn();

		trackCreatedRate(result);
	}

	@Test
	void createRateWithUnknownRoomTypeReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/rates")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRateJson(UUID.randomUUID())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith("Room type not found")));
	}

	@Test
	void createRateWithNegativePriceReturnsBadRequest() throws Exception {
		RoomType roomType = createRoomType();

		mockMvc.perform(post("/api/v1/rates")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomTypeId": "%s", "name": "Invalid", "validFrom": "2026-01-01",
								 "priceCents": -1, "minimumNights": 1}
								""".formatted(roomType.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.priceCents").exists());
	}

	@Test
	void createRateWithInvalidDateRangeReturnsBadRequest() throws Exception {
		RoomType roomType = createRoomType();

		mockMvc.perform(post("/api/v1/rates")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomTypeId": "%s", "name": "Invalid", "validFrom": "2026-06-10",
								 "validTo": "2026-06-01", "priceCents": 10000, "minimumNights": 1}
								""".formatted(roomType.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Valid to must be on or after valid from"));
	}

	@Test
	void createRateWithInvalidFieldsReturnsBadRequest() throws Exception {
		RoomType roomType = createRoomType();

		mockMvc.perform(post("/api/v1/rates")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"roomTypeId": "%s", "name": "Invalid", "validFrom": "2026-01-01",
								 "priceCents": 10000, "minimumNights": 0, "currency": "USD"}
								""".formatted(roomType.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.minimumNights").exists())
				.andExpect(jsonPath("$.errors.currency").exists());
	}

	@Test
	void updateRateAppliesPartialChanges() throws Exception {
		RoomType roomType = createRoomType();
		Rate rate = createRate(roomType);

		mockMvc.perform(put("/api/v1/rates/{id}", rate.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"priceCents\": 52000, \"refundable\": false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.priceCents").value(52000))
				.andExpect(jsonPath("$.refundable").value(false))
				.andExpect(jsonPath("$.name").value(rate.getName()))
				.andExpect(jsonPath("$.validFrom").value("2026-01-01"))
				.andExpect(jsonPath("$.roomTypeId").value(roomType.getId().toString()));
	}

	@Test
	void updateRateValidatesDateRangeAgainstStoredValues() throws Exception {
		Rate rate = createRate(createRoomType());

		// validFrom guardado es 2026-01-01; un validTo anterior debe rechazarse.
		mockMvc.perform(put("/api/v1/rates/{id}", rate.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"validTo\": \"2025-12-31\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updateRateWithUnknownRoomTypeReturnsBadRequest() throws Exception {
		Rate rate = createRate(createRoomType());

		mockMvc.perform(put("/api/v1/rates/{id}", rate.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"roomTypeId\": \"%s\"}".formatted(UUID.randomUUID())))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updateMissingRateReturnsNotFound() throws Exception {
		mockMvc.perform(put("/api/v1/rates/{id}", UUID.randomUUID())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"priceCents\": 1000}"))
				.andExpect(status().isNotFound());
	}

	private static String validRateJson(UUID roomTypeId) {
		return """
				{"roomTypeId": "%s", "name": "Base", "validFrom": "2026-01-01",
				 "priceCents": 45000, "minimumNights": 1}
				""".formatted(roomTypeId);
	}
}
