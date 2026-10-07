package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.aurora.pms.repository.ProductRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * #84: la respuesta de producto no expone reorderLevel, así que un cliente
 * que edita no puede reenviarlo. Un PUT sin reorderLevel debe conservar el
 * valor guardado en vez de reiniciarlo a 0.
 */
class AdminProductReorderLevelTest extends AbstractCatalogApiTest {

	@Autowired
	private ProductRepository productRepository;

	private final List<UUID> productIds = new ArrayList<>();

	@AfterEach
	void cleanUpProducts() {
		productRepository.deleteAllById(productIds);
	}

	@Test
	void updateWithoutReorderLevelKeepsTheStoredValue() throws Exception {
		String sku = "RL-" + uniqueSuffix();
		UUID id = create("""
				{"sku": "%s", "name": "Cafe", "category": "food_and_beverage",
				 "priceCents": 1800, "reorderLevel": 8}
				""".formatted(sku));
		assertThat(reorderLevelOf(id)).isEqualTo(8);

		update(id, """
				{"sku": "%s", "name": "Cafe", "category": "food_and_beverage",
				 "priceCents": 1800, "active": false}
				""".formatted(sku));
		assertThat(reorderLevelOf(id)).as("desactivar sin reorderLevel lo conserva").isEqualTo(8);

		update(id, """
				{"sku": "%s", "name": "Cafe de olla", "description": "Con canela", "category": "food_and_beverage",
				 "priceCents": 2000}
				""".formatted(sku));
		assertThat(reorderLevelOf(id)).as("editar otros campos lo conserva").isEqualTo(8);
	}

	@Test
	void updateWithReorderLevelChangesIt() throws Exception {
		String sku = "RL-" + uniqueSuffix();
		UUID id = create("""
				{"sku": "%s", "name": "Agua", "category": "minibar", "priceCents": 1200, "reorderLevel": 6}
				""".formatted(sku));

		update(id, """
				{"sku": "%s", "name": "Agua", "category": "minibar", "priceCents": 1200, "reorderLevel": 0}
				""".formatted(sku));

		assertThat(reorderLevelOf(id)).as("un valor explícito, incluso 0, se guarda").isZero();
	}

	@Test
	void createWithoutReorderLevelStartsAtZero() throws Exception {
		UUID id = create("""
				{"sku": "RL-%s", "name": "Pan", "category": "food_and_beverage", "priceCents": 900}
				""".formatted(uniqueSuffix()));

		assertThat(reorderLevelOf(id)).isZero();
	}

	private UUID create(String body) throws Exception {
		String response = mockMvc.perform(post("/api/v1/admin/room-service/products")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		UUID id = UUID.fromString(JsonPath.read(response, "$.id"));
		productIds.add(id);
		return id;
	}

	private void update(UUID id, String body) throws Exception {
		mockMvc.perform(put("/api/v1/admin/room-service/products/{id}", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk());
	}

	private int reorderLevelOf(UUID id) {
		return productRepository.findById(id).orElseThrow().getReorderLevel();
	}
}
