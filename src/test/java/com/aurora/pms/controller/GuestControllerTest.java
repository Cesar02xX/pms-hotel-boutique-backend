package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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

import com.aurora.pms.model.Guest;
import com.jayway.jsonpath.JsonPath;

class GuestControllerTest extends AbstractCatalogApiTest {

	@Test
	void listGuestsReturnsOkIncludingExistingGuest() throws Exception {
		Guest guest = createGuest();

		mockMvc.perform(get("/api/v1/guests").with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(guest.getId().toString())));
	}

	@Test
	void listGuestsReturnsEmptyListWhenNoGuestsExist() throws Exception {
		mockMvc.perform(get("/api/v1/guests").with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void getGuestByIdReturnsGuest() throws Exception {
		Guest guest = createGuest();

		mockMvc.perform(get("/api/v1/guests/{id}", guest.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(guest.getId().toString()))
				.andExpect(jsonPath("$.firstName").value(guest.getFirstName()))
				.andExpect(jsonPath("$.lastName").value(guest.getLastName()))
				.andExpect(jsonPath("$.email").value(guest.getEmail()))
				.andExpect(jsonPath("$.documentType").value("passport"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.updatedAt").exists());
	}

	@Test
	void getMissingGuestReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/guests/{id}", UUID.randomUUID()).with(staffUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Guest not found")));
	}

	@Test
	void getGuestWithInvalidUuidReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/guests/{id}", "GST-001").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void createGuestReturnsCreated() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/guests")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": "Ana", "lastName": "Lopez", "email": "ana.lopez@example.com",
								 "phone": "+502 5555 0202", "nationality": "GT", "documentType": "passport",
								 "documentNumber": "P123456", "notes": "Prefiere habitacion tranquila"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.firstName").value("Ana"))
				.andExpect(jsonPath("$.lastName").value("Lopez"))
				.andExpect(jsonPath("$.email").value("ana.lopez@example.com"))
				.andExpect(jsonPath("$.documentType").value("passport"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.updatedAt").exists())
				.andReturn();

		UUID id = trackCreatedGuest(result);
		assertThat(guestRepository.findById(id)).isPresent();
	}

	@Test
	void createGuestIgnoresClientControlledFields() throws Exception {
		UUID clientId = UUID.randomUUID();

		MvcResult result = mockMvc.perform(post("/api/v1/guests")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"id": "%s", "firstName": "Luis", "lastName": "Garcia",
								 "createdAt": "2000-01-01T00:00:00Z", "updatedAt": "2000-01-01T00:00:00Z"}
								""".formatted(clientId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(not(clientId.toString())))
				.andExpect(jsonPath("$.createdAt").value(not("2000-01-01T00:00:00Z")))
				.andReturn();

		trackCreatedGuest(result);
	}

	@Test
	void createGuestWithInvalidRequestReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/guests")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": " ", "email": "not-an-email"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.firstName").exists())
				.andExpect(jsonPath("$.errors.lastName").exists())
				.andExpect(jsonPath("$.errors.email").exists());
	}

	@Test
	void createGuestWithUnknownDocumentTypeReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/guests")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": "Ana", "lastName": "Lopez", "documentType": "dpi"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void updateGuestReturnsOkAndKeepsImmutableFields() throws Exception {
		Guest guest = createGuest();
		String before = mockMvc.perform(get("/api/v1/guests/{id}", guest.getId()).with(staffUser()))
				.andReturn().getResponse().getContentAsString();

		mockMvc.perform(put("/api/v1/guests/{id}", guest.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": "Maria", "lastName": "Perez", "email": "maria.perez@example.com",
								 "phone": "+502 5555 0303", "nationality": "SV", "documentType": "national_id",
								 "documentNumber": "DPI-123", "notes": "Actualizada"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(guest.getId().toString()))
				.andExpect(jsonPath("$.firstName").value("Maria"))
				.andExpect(jsonPath("$.lastName").value("Perez"))
				.andExpect(jsonPath("$.email").value("maria.perez@example.com"))
				.andExpect(jsonPath("$.documentType").value("national_id"))
				.andExpect(jsonPath("$.createdAt").value((String) JsonPath.read(before, "$.createdAt")))
				.andExpect(jsonPath("$.updatedAt").value(not((String) JsonPath.read(before, "$.updatedAt"))));
	}

	@Test
	void updateMissingGuestReturnsNotFound() throws Exception {
		mockMvc.perform(put("/api/v1/guests/{id}", UUID.randomUUID())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": "Maria", "lastName": "Perez"}
								"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void updateGuestWithInvalidRequestReturnsBadRequest() throws Exception {
		Guest guest = createGuest();

		mockMvc.perform(put("/api/v1/guests/{id}", guest.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": "", "lastName": "Perez", "email": "bad"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.firstName").exists())
				.andExpect(jsonPath("$.errors.email").exists());
	}
}
