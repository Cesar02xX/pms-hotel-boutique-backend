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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
	void listGuestsReturnsOkWithoutRequiringAnEmptyDatabase() throws Exception {
		mockMvc.perform(get("/api/v1/guests").with(staffUser()))
				.andExpect(status().isOk());
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

	@Test
	void createGuestWithDuplicateEmailReturnsConflict() throws Exception {
		Guest existing = createGuest();

		mockMvc.perform(post("/api/v1/guests")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": "Ana", "lastName": "Lopez", "email": "%s"}
								""".formatted(existing.getEmail().toUpperCase())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value(startsWith("Guest email already exists")));
	}

	@Test
	void createGuestWithDuplicateDocumentReturnsConflict() throws Exception {
		Guest existing = createGuest();

		mockMvc.perform(post("/api/v1/guests")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": "Ana", "lastName": "Lopez", "documentType": "passport",
								 "documentNumber": "%s"}
								""".formatted(existing.getDocumentNumber())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value(startsWith("Guest document already exists")));
	}

	@Test
	void concurrentCreateGuestWithSameEmailAndDocumentReturnsOneCreatedAndOneConflict() throws Exception {
		String suffix = uniqueSuffix();
		String email = "concurrent.%s@aurora.test".formatted(suffix);
		String documentNumber = "CON-" + suffix;
		String body = """
				{"firstName": "Ana", "lastName": "Lopez", "email": "%s",
				 "phone": "+502 5555 0202", "nationality": "GT", "documentType": "passport",
				 "documentNumber": "%s"}
				""".formatted(email, documentNumber);

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			List<Future<MvcResult>> results = List.of(
					executor.submit(() -> performConcurrentCreate(body, ready, start)),
					executor.submit(() -> performConcurrentCreate(body, ready, start))
			);

			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<MvcResult> responses = results.stream()
					.map(this::getMvcResult)
					.toList();

			assertThat(responses)
					.extracting(response -> response.getResponse().getStatus())
					.containsExactlyInAnyOrder(201, 409);

			responses.stream()
					.filter(response -> response.getResponse().getStatus() == 201)
					.findFirst()
					.ifPresent(result -> {
						try {
							trackCreatedGuest(result);
						} catch (Exception exception) {
							throw new AssertionError("Could not track created guest", exception);
						}
					});

			assertThat(guestRepository.findAll().stream()
					.filter(guest -> email.equalsIgnoreCase(guest.getEmail()))
					.filter(guest -> "passport".equals(guest.getDocumentType().name()))
					.filter(guest -> documentNumber.equals(guest.getDocumentNumber()))
					.count()).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void createGuestWithSameDocumentNumberButDifferentTypeReturnsCreated() throws Exception {
		Guest existing = createGuest();

		MvcResult result = mockMvc.perform(post("/api/v1/guests")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": "Ana", "lastName": "Lopez", "documentType": "national_id",
								 "documentNumber": "%s"}
								""".formatted(existing.getDocumentNumber())))
				.andExpect(status().isCreated())
				.andReturn();

		trackCreatedGuest(result);
	}

	@Test
	void createGuestsWithoutOptionalIdentifiersDoesNotConflict() throws Exception {
		String[] bodies = {
				"""
				{"firstName": "Ana", "lastName": "Lopez"}
				""",
				"""
				{"firstName": "Luis", "lastName": "Garcia", "email": "", "documentType": "passport",
				 "documentNumber": "  "}
				""",
				"""
				{"firstName": "Sofia", "lastName": "Ramirez", "documentType": "passport"}
				""",
				"""
				{"firstName": "Carlos", "lastName": "Mendez", "documentType": "passport"}
				"""
		};

		for (String body : bodies) {
			MvcResult result = mockMvc.perform(post("/api/v1/guests")
							.with(staffUser())
							.contentType(MediaType.APPLICATION_JSON)
							.content(body))
					.andExpect(status().isCreated())
					.andReturn();
			trackCreatedGuest(result);
		}
	}

	@Test
	void updateGuestWithAnotherGuestEmailReturnsConflict() throws Exception {
		Guest existing = createGuest();
		Guest guest = createGuest();

		mockMvc.perform(put("/api/v1/guests/{id}", guest.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": "Maria", "lastName": "Perez", "email": "%s"}
								""".formatted(existing.getEmail())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value(startsWith("Guest email already exists")));

		assertThat(guestRepository.findById(guest.getId()).orElseThrow().getEmail()).isEqualTo(guest.getEmail());
	}

	@Test
	void updateGuestWithAnotherGuestDocumentReturnsConflict() throws Exception {
		Guest existing = createGuest();
		Guest guest = createGuest();

		mockMvc.perform(put("/api/v1/guests/{id}", guest.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": "Maria", "lastName": "Perez", "documentType": "passport",
								 "documentNumber": "%s"}
								""".formatted(existing.getDocumentNumber())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.message").value(startsWith("Guest document already exists")));

		assertThat(guestRepository.findById(guest.getId()).orElseThrow().getDocumentNumber())
				.isEqualTo(guest.getDocumentNumber());
	}

	@Test
	void updateGuestKeepingOwnEmailAndDocumentReturnsOk() throws Exception {
		Guest guest = createGuest();

		mockMvc.perform(put("/api/v1/guests/{id}", guest.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"firstName": "Maria", "lastName": "Perez", "email": "%s",
								 "documentType": "passport", "documentNumber": "%s"}
								""".formatted(guest.getEmail(), guest.getDocumentNumber())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName").value("Maria"))
				.andExpect(jsonPath("$.email").value(guest.getEmail()))
				.andExpect(jsonPath("$.documentNumber").value(guest.getDocumentNumber()));
	}

	private MvcResult performConcurrentCreate(String body, CountDownLatch ready, CountDownLatch start) throws Exception {
		ready.countDown();
		assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
		return mockMvc.perform(post("/api/v1/guests")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andReturn();
	}

	private MvcResult getMvcResult(Future<MvcResult> future) {
		try {
			return future.get(5, TimeUnit.SECONDS);
		} catch (Exception exception) {
			throw new AssertionError("Concurrent guest creation did not finish", exception);
		}
	}
}
