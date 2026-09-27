package com.aurora.pms.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.aurora.pms.model.RoomFeature;

class RoomFeatureControllerTest extends AbstractCatalogApiTest {

	@Test
	void listRoomFeaturesReturnsOkWithFeatureFields() throws Exception {
		RoomFeature roomFeature = createRoomFeature("Garden view");
		String featurePath = "$[?(@.id == '%s')]".formatted(roomFeature.getId());

		mockMvc.perform(get("/api/v1/room-features").with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath(featurePath + ".name").value(roomFeature.getName()))
				.andExpect(jsonPath(featurePath + ".description").value("Test feature"))
				.andExpect(jsonPath(featurePath + ".createdAt").exists())
				.andExpect(jsonPath(featurePath + ".updatedAt").exists());
	}
}
