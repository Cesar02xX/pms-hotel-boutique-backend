package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.aurora.pms.model.Amenity;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.AmenityCategory;
import com.aurora.pms.repository.AmenityRepository;
import com.aurora.pms.repository.InventoryItemRepository;
import com.aurora.pms.repository.MediaImageRepository;
import com.aurora.pms.repository.ProductRepository;
import com.aurora.pms.security.SecurityPermissions;
import com.aurora.pms.support.InMemoryObjectStorage;
import com.aurora.pms.support.TestImages;
import com.jayway.jsonpath.JsonPath;

/**
 * Flujo completo de imágenes de catálogo (#82): subir, asociar, publicar,
 * ocultar al desactivar, permisos y errores de validación.
 */
@Import(MediaApiTest.StorageConfig.class)
class MediaApiTest extends AbstractCatalogApiTest {

	@TestConfiguration
	static class StorageConfig {

		@Bean
		@Primary
		InMemoryObjectStorage inMemoryObjectStorage() {
			return new InMemoryObjectStorage();
		}
	}

	@Autowired
	private InMemoryObjectStorage storage;

	@Autowired
	private MediaImageRepository mediaImageRepository;

	@Autowired
	private AmenityRepository amenityRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private InventoryItemRepository inventoryItemRepository;

	private final List<UUID> mediaIds = new ArrayList<>();
	private final List<UUID> amenityIds = new ArrayList<>();
	private final List<UUID> productIds = new ArrayList<>();
	private final List<UUID> inventoryItemIds = new ArrayList<>();

	@AfterEach
	void cleanUpMedia() {
		mediaImageRepository.deleteAllById(mediaIds);
		amenityRepository.deleteAllById(amenityIds);
		productRepository.deleteAllById(productIds);
		inventoryItemRepository.deleteAllById(inventoryItemIds);
	}

	@Test
	void roomTypeImageIsPublishedOnlyWhileAttachedToAnActiveRoomType() throws Exception {
		RoomType roomType = createRoomType();
		UUID mediaId = upload("room_type", staffUser());

		// Pendiente: no se sirve sin sesión.
		mockMvc.perform(get("/api/v1/public/media/{id}/thumb", mediaId))
				.andExpect(status().isNotFound());

		mockMvc.perform(put("/api/v1/room-types/{id}", roomType.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"images": [{"mediaId": "%s", "altText": "Cama king con vista"}]}
								""".formatted(mediaId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.images[0].id").value(mediaId.toString()))
				.andExpect(jsonPath("$.images[0].primary").value(true))
				.andExpect(jsonPath("$.images[0].position").value(0))
				.andExpect(jsonPath("$.images[0].altText").value("Cama king con vista"))
				.andExpect(jsonPath("$.images[0].urls.thumb")
						.value("http://localhost:8080/api/v1/public/media/" + mediaId + "/thumb"));

		mockMvc.perform(get("/api/v1/public/room-types"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == '%s')].images[0].id".formatted(roomType.getId()))
						.value(mediaId.toString()));

		mockMvc.perform(get("/api/v1/public/media/{id}/medium", mediaId))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/jpeg"))
				.andExpect(header().string("Cache-Control", "max-age=3600, public"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));

		mockMvc.perform(put("/api/v1/room-types/{id}", roomType.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\": false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.images[0].id").value(mediaId.toString()));

		// Registro inactivo: oculta al público, visible para el personal.
		mockMvc.perform(get("/api/v1/public/media/{id}/medium", mediaId))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/media/{id}/content/medium", mediaId).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "private, no-store"));
	}

	@Test
	void removingImagesFromRoomTypeLeavesThemPendingAndHidden() throws Exception {
		RoomType roomType = createRoomType();
		UUID first = upload("room_type", staffUser());
		UUID second = upload("room_type", staffUser());

		mockMvc.perform(put("/api/v1/room-types/{id}", roomType.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"images": [{"mediaId": "%s"}, {"mediaId": "%s", "primary": true}]}
								""".formatted(first, second)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.images[0].primary").value(false))
				.andExpect(jsonPath("$.images[1].primary").value(true));

		mockMvc.perform(put("/api/v1/room-types/{id}", roomType.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"images": [{"mediaId": "%s"}]}
								""".formatted(second)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.images.length()").value(1))
				.andExpect(jsonPath("$.images[0].primary").value(true));

		assertThat(mediaImageRepository.findById(first).orElseThrow().isPending()).isTrue();
		mockMvc.perform(get("/api/v1/public/media/{id}/thumb", first))
				.andExpect(status().isNotFound());

		// Una imagen pendiente se puede borrar; una asociada no.
		mockMvc.perform(delete("/api/v1/media/{id}", first).with(staffUser()))
				.andExpect(status().isNoContent());
		assertThat(storage.contains("media/" + first + "/original")).isFalse();
		mockMvc.perform(delete("/api/v1/media/{id}", second).with(staffUser()))
				.andExpect(status().isConflict());
	}

	@Test
	void productAndAmenityPersistImagesOnCreateAndExposeThem() throws Exception {
		UUID productImage = upload("product", staffUser());
		String productResponse = mockMvc.perform(post("/api/v1/admin/room-service/products")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"sku": "IMG-%s", "name": "Club sándwich", "category": "food_and_beverage",
								 "priceCents": 8500, "images": [{"mediaId": "%s"}]}
								""".formatted(uniqueSuffix(), productImage)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.images[0].id").value(productImage.toString()))
				.andReturn().getResponse().getContentAsString();
		productIds.add(UUID.fromString(JsonPath.read(productResponse, "$.id")));

		Amenity amenity = new Amenity();
		amenity.setName("Piscina " + uniqueSuffix());
		amenity.setCategory(AmenityCategory.hotel);
		amenity.setCreatedAt(now());
		amenity.setUpdatedAt(now());
		amenity = amenityRepository.save(amenity);
		amenityIds.add(amenity.getId());
		UUID amenityImage = upload("amenity", staffUser());

		mockMvc.perform(put("/api/v1/admin/amenities/{id}", amenity.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "%s", "category": "hotel", "images": [{"mediaId": "%s"}]}
								""".formatted(amenity.getName(), amenityImage)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.images[0].id").value(amenityImage.toString()));

		mockMvc.perform(get("/api/v1/public/amenities"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == '%s')].images[0].id".formatted(amenity.getId()))
						.value(amenityImage.toString()));
		mockMvc.perform(get("/api/v1/public/media/{id}/thumb", amenityImage))
				.andExpect(status().isOk());
	}

	@Test
	void inventoryItemPersistsImageAndReturnsItOnSubsequentReads() throws Exception {
		UUID imageId = upload("inventory_item", userWithPermissions(
				"inventory.editor@aurora.test", SecurityPermissions.INVENTORY_WRITE));
		String response = mockMvc.perform(post("/api/v1/admin/inventory/items")
						.with(userWithPermissions("inventory.editor@aurora.test", SecurityPermissions.INVENTORY_WRITE))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Toallas", "category": "housekeeping",
								 "unit": "unit", "images": [{"mediaId": "%s"}]}
								""".formatted(imageId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sku").isNotEmpty())
				.andExpect(jsonPath("$.images[0].id").value(imageId.toString()))
				.andExpect(jsonPath("$.images[0].primary").value(true))
				.andReturn().getResponse().getContentAsString();
		UUID itemId = UUID.fromString(JsonPath.read(response, "$.id"));
		String generatedSku = JsonPath.read(response, "$.sku");
		assertThat(generatedSku).matches("INV-[0-9A-F]{32}");
		inventoryItemIds.add(itemId);
		mockMvc.perform(put("/api/v1/admin/inventory/items/{id}", itemId)
						.with(userWithPermissions("inventory.editor@aurora.test", SecurityPermissions.INVENTORY_WRITE))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Toallas premium", "category": "housekeeping", "unit": "unit"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sku").value(generatedSku));

		mockMvc.perform(get("/api/v1/inventory/items").with(userWithPermissions(
					"inventory.editor@aurora.test", SecurityPermissions.INVENTORY_READ)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == '%s')].images[0].id".formatted(itemId))
						.value(imageId.toString()));
		mockMvc.perform(get("/api/v1/public/media/{id}/thumb", imageId))
				.andExpect(status().isOk());
	}

	@Test
	void imageCannotBeAttachedToAnotherTargetOrRecord() throws Exception {
		RoomType first = createRoomType();
		RoomType second = createRoomType();
		UUID productImage = upload("product", staffUser());
		UUID roomImage = upload("room_type", staffUser());

		mockMvc.perform(put("/api/v1/room-types/{id}", first.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"images\": [{\"mediaId\": \"%s\"}]}".formatted(productImage)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/room-types/{id}", first.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"images\": [{\"mediaId\": \"%s\"}]}".formatted(roomImage)))
				.andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/room-types/{id}", second.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"images\": [{\"mediaId\": \"%s\"}]}".formatted(roomImage)))
				.andExpect(status().isConflict());
	}

	@Test
	void uploadRequiresThePermissionOfItsTarget() throws Exception {
		RequestPostProcessor roomsEditor = userWithPermissions("rooms.editor@aurora.test",
				SecurityPermissions.ROOMS_WRITE);

		mockMvc.perform(multipart("/api/v1/media").file(jpegFile()).param("target", "room_type").with(roomsEditor))
				.andExpect(status().isForbidden());
		upload("amenity", roomsEditor);

		mockMvc.perform(multipart("/api/v1/media").file(jpegFile()).param("target", "amenity"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void uploadRejectsInvalidFilesWithClearErrors() throws Exception {
		MockMultipartFile svg = new MockMultipartFile("file", "logo.jpg", "image/jpeg",
				"<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes());
		mockMvc.perform(multipart("/api/v1/media").file(svg).param("target", "product").with(staffUser()))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.message").value("Only JPEG, PNG and WebP images are allowed"));

		MockMultipartFile tooLarge = new MockMultipartFile("file", "big.jpg", "image/jpeg",
				new byte[5 * 1024 * 1024 + 1]);
		mockMvc.perform(multipart("/api/v1/media").file(tooLarge).param("target", "product").with(staffUser()))
				.andExpect(status().isPayloadTooLarge());

		MockMultipartFile corrupt = new MockMultipartFile("file", "broken.jpg", "image/jpeg",
				TestImages.corruptJpeg());
		mockMvc.perform(multipart("/api/v1/media").file(corrupt).param("target", "product").with(staffUser()))
				.andExpect(status().isBadRequest());

		mockMvc.perform(multipart("/api/v1/media").file(jpegFile()).with(staffUser()))
				.andExpect(status().isBadRequest());
		mockMvc.perform(multipart("/api/v1/media").file(jpegFile()).param("target", "poster").with(staffUser()))
				.andExpect(status().isBadRequest());
		mockMvc.perform(multipart("/api/v1/media").param("target", "product").with(staffUser()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unknownPublicImageReturnsNotFoundWithoutSession() throws Exception {
		mockMvc.perform(get("/api/v1/public/media/{id}/thumb", UUID.randomUUID()))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/public/media/{id}/original", UUID.randomUUID()))
				.andExpect(status().isBadRequest());
	}

	private UUID upload(String target, RequestPostProcessor user) throws Exception {
		String response = mockMvc.perform(multipart("/api/v1/media")
						.file(jpegFile())
						.param("target", target)
						.with(user))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.target").value(target))
				.andReturn().getResponse().getContentAsString();
		UUID id = UUID.fromString(JsonPath.read(response, "$.id"));
		mediaIds.add(id);
		return id;
	}

	private static MockMultipartFile jpegFile() {
		return new MockMultipartFile("file", "habitacion.jpg", "image/jpeg", TestImages.jpeg(1200, 800));
	}
}
