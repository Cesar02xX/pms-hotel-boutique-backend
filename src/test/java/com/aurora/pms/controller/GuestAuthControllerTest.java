package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import com.aurora.pms.dto.request.CreateGuestServiceRequest;
import com.aurora.pms.dto.request.GuestLoginRequest;
import com.aurora.pms.dto.response.ConciergeRequestResponse;
import com.aurora.pms.dto.response.GuestLinkResponse;
import com.aurora.pms.dto.response.GuestLoginResponse;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.GuestCredential;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.repository.GuestCredentialRepository;
import com.aurora.pms.repository.ServiceRequestRepository;
import com.aurora.pms.service.GuestAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Login y aislamiento del portal de huésped. Cada prueba crea su propio
 * huésped con una estancia relativa a hoy: no depende de las reservas demo del
 * seed, cuyas fechas son fijas y vencen (020-real-demo-seed.sql). Que el seed
 * trae las credenciales demo lo verifica DemoSeedDataTest.
 */
class GuestAuthControllerTest extends AbstractCatalogApiTest {

	private static final String LOGIN_PATH = "/api/v1/guest/auth/login";
	private static final String LINK_PATH = "/api/v1/guest/auth/link";
	private static final String REGISTER_PATH = "/api/v1/guest/auth/register";
	private static final String STAY_PATH = "/api/v1/guest/stay";
	private static final String PASSWORD = "password123";
	private static final ZoneId HOTEL_ZONE = ZoneId.of("America/Guatemala");

	@Autowired
	private GuestCredentialRepository guestCredentialRepository;

	@Autowired
	private ServiceRequestRepository serviceRequestRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private GuestAccessService guestAccessService;

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final List<UUID> testCredentialIds = new ArrayList<>();
	private final List<UUID> testServiceRequestIds = new ArrayList<>();

	@AfterEach
	void cleanUpCredentials() {
		serviceRequestRepository.deleteAllById(testServiceRequestIds);
		testServiceRequestIds.clear();
		guestCredentialRepository.deleteAllById(testCredentialIds);
		testCredentialIds.clear();
	}

	@Test
	void guestWithActiveStayCanLoginAndAccessOwnStay() throws Exception {
		Guest guest = createGuest();
		createActiveBookingFor(guest);
		GuestCredential credential = createCredential(guest, uniqueEmail("active"), PASSWORD, true);

		MvcResult loginResult = mockMvc.perform(post(LOGIN_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(credential.getEmail(), PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken", notNullValue()))
				.andExpect(jsonPath("$.tokenType", is("Bearer")))
				.andExpect(jsonPath("$.expiresIn", notNullValue()))
				.andReturn();

		GuestLoginResponse response = objectMapper.readValue(
				loginResult.getResponse().getContentAsString(),
				GuestLoginResponse.class
		);

		mockMvc.perform(get(STAY_PATH)
						.header("Authorization", "Bearer " + response.accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.guestFirstName", is(guest.getFirstName())))
				.andExpect(jsonPath("$.guestLastName", is(guest.getLastName())));
	}

	@Test
	void loginWithInvalidPasswordReturns401Controlled() throws Exception {
		Guest guest = createGuest();
		createActiveBookingFor(guest);
		GuestCredential credential = createCredential(guest, uniqueEmail("wrong"), PASSWORD, true);

		mockMvc.perform(post(LOGIN_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(credential.getEmail(), "wrong-password")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status", is(401)))
				.andExpect(jsonPath("$.error", is("Unauthorized")))
				.andExpect(jsonPath("$.message", is("Invalid email or password")));
	}

	@Test
	void loginWithUnknownEmailReturns401Controlled() throws Exception {
		mockMvc.perform(post(LOGIN_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(uniqueEmail("unknown"), PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status", is(401)))
				.andExpect(jsonPath("$.error", is("Unauthorized")))
				.andExpect(jsonPath("$.message", is("Invalid email or password")));
	}

	@Test
	void loginWithBlankFieldsReturns400Validation() throws Exception {
		mockMvc.perform(post(LOGIN_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"\",\"password\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status", is(400)))
				.andExpect(jsonPath("$.error", is("Bad Request")));
	}

	@Test
	void loginWithInactiveCredentialReturns401() throws Exception {
		Guest guest = createGuest();
		createActiveBookingFor(guest);
		GuestCredential credential = createCredential(guest, uniqueEmail("inactive"), PASSWORD, false);

		mockMvc.perform(post(LOGIN_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(credential.getEmail(), PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message", is("Invalid email or password")));
	}

	@Test
	void loginWithoutActiveStayReturns400() throws Exception {
		Guest guest = createGuest();
		// Reserva sin check-in (pending/confirmed)
		RoomType roomType = createRoomType();
		createBooking(guest, roomType, createRoom(roomType), createRate(roomType));
		GuestCredential credential = createCredential(guest, uniqueEmail("no.stay"), PASSWORD, true);

		mockMvc.perform(post(LOGIN_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(credential.getEmail(), PASSWORD)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", is("Guest has no active stay")));
	}

	@Test
	void loginOnCheckOutDayReturns400StayExpired() throws Exception {
		Guest guest = createGuest();
		LocalDate today = LocalDate.now(HOTEL_ZONE);
		// La estancia termina hoy: el día de salida ya no cuenta como estancia activa.
		createCheckedInBookingFor(guest, today.minusDays(3), today);
		GuestCredential credential = createCredential(guest, uniqueEmail("expired"), PASSWORD, true);

		mockMvc.perform(post(LOGIN_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(credential.getEmail(), PASSWORD)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", is("La estancia no está activa: ya venció o todavía no ha comenzado.")));
	}

	@Test
	void isolationBetweenGuestsPreventsCrossAccessToOtherBookingResources() throws Exception {
		Guest ana = createGuest();
		createActiveBookingFor(ana);
		GuestCredential anaCredential = createCredential(ana, uniqueEmail("ana"), PASSWORD, true);
		Guest carlos = createGuest();
		Booking carlosBooking = createActiveBookingFor(carlos);
		GuestCredential carlosCredential = createCredential(carlos, uniqueEmail("carlos"), PASSWORD, true);

		GuestLoginResponse anaAuth = login(anaCredential.getEmail(), PASSWORD);
		GuestLoginResponse carlosAuth = login(carlosCredential.getEmail(), PASSWORD);

		// Carlos crea una solicitud de conserjería
		ConciergeRequestResponse carlosRequest = guestAccessService.createConciergeRequest(
				carlosBooking.getId(),
				new CreateGuestServiceRequest("Taxi para el aeropuerto", "Favor reservar para las 10:00")
		);
		testServiceRequestIds.add(carlosRequest.id());

		// Ana intenta consultar la solicitud de Carlos con su token -> 403 Forbidden
		mockMvc.perform(get("/api/v1/guest/concierge/requests/" + carlosRequest.id())
						.header("Authorization", "Bearer " + anaAuth.accessToken()))
				.andExpect(status().isForbidden());

		// Ana intenta cancelar la solicitud de Carlos con su token -> 403 Forbidden
		mockMvc.perform(post("/api/v1/guest/concierge/requests/" + carlosRequest.id() + "/cancel")
						.header("Authorization", "Bearer " + anaAuth.accessToken()))
				.andExpect(status().isForbidden());

		// Carlos sí puede consultar su propia solicitud
		mockMvc.perform(get("/api/v1/guest/concierge/requests/" + carlosRequest.id())
						.header("Authorization", "Bearer " + carlosAuth.accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(carlosRequest.id().toString())));
	}

	@Test
	void guestTokenCannotAccessStaffProtectedEndpoints() throws Exception {
		Guest guest = createGuest();
		createActiveBookingFor(guest);
		GuestCredential credential = createCredential(guest, uniqueEmail("staff.check"), PASSWORD, true);
		GuestLoginResponse guestAuth = login(credential.getEmail(), PASSWORD);

		mockMvc.perform(get("/api/v1/rooms")
						.header("Authorization", "Bearer " + guestAuth.accessToken()))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/guests")
						.header("Authorization", "Bearer " + guestAuth.accessToken()))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/admin/users")
						.header("Authorization", "Bearer " + guestAuth.accessToken()))
				.andExpect(status().isForbidden());
	}

	@Test
	void deprecatedLinkEndpointStillAuthenticatesGuest() throws Exception {
		Booking booking = createActiveBookingFor(createGuest());

		mockMvc.perform(post(LINK_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"" + booking.getGuestLinkCode() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken", notNullValue()))
				.andExpect(jsonPath("$.tokenType", is("Bearer")));
	}

	@Test
	void reservationConfirmationCodeAuthenticatesGuestDuringActiveStay() throws Exception {
		Booking booking = createActiveBookingFor(createGuest());

		mockMvc.perform(post(LINK_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("code", booking.getConfirmationCode()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken", notNullValue()))
				.andExpect(jsonPath("$.tokenType", is("Bearer")));
	}

	@Test
	void reservationCodeOpensUpcomingBookingButDoesNotAllowStayServicesBeforeCheckIn() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();
		Booking booking = createBooking(guest, roomType, null, null);
		LocalDate today = LocalDate.now(HOTEL_ZONE);
		booking.setCheckIn(today.plusDays(7));
		booking.setCheckOut(today.plusDays(10));
		booking.setStatus(BookingStatus.pending);
		booking = bookingRepository.saveAndFlush(booking);

		MvcResult linkResult = mockMvc.perform(post(LINK_PATH)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("code", booking.getConfirmationCode()))))
			.andReturn();
		assertThat(linkResult.getResponse().getStatus())
				.withFailMessage(linkResult.getResponse().getContentAsString())
				.isEqualTo(200);
		GuestLinkResponse access = objectMapper.readValue(
				linkResult.getResponse().getContentAsString(), GuestLinkResponse.class);
		String authorization = "Bearer " + access.accessToken();

		mockMvc.perform(get(STAY_PATH).header("Authorization", authorization))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("pending")))
				.andExpect(jsonPath("$.guestFirstName", is("Test")));

		mockMvc.perform(get("/api/v1/guest/concierge/requests").header("Authorization", authorization))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/guest/concierge/requests")
					.header("Authorization", authorization)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"description\":\"Taxi\",\"notes\":\"Before check-in\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void reservationCodeAndMatchingEmailCreateGuestAccountAndSignIn() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();
		Booking booking = createBooking(guest, roomType, null, null);
		LocalDate today = LocalDate.now(HOTEL_ZONE);
		booking.setCheckIn(today.plusDays(3));
		booking.setCheckOut(today.plusDays(5));
		booking.setStatus(BookingStatus.confirmed);
		booking = bookingRepository.saveAndFlush(booking);

		MvcResult result = mockMvc.perform(post(REGISTER_PATH)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
							"code", booking.getConfirmationCode(),
							"email", guest.getEmail().toUpperCase(),
							"password", "new-password-123"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken", notNullValue()))
				.andReturn();
		GuestLinkResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), GuestLinkResponse.class);
		mockMvc.perform(get(STAY_PATH).header("Authorization", "Bearer " + response.accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.guestFirstName", is("Test")));

		GuestCredential credential = guestCredentialRepository.findByGuestId(guest.getId()).orElseThrow();
		testCredentialIds.add(credential.getId());
		assertThat(passwordEncoder.matches("new-password-123", credential.getPasswordHash())).isTrue();
	}

	@Test
	void registrationRejectsEmailThatDoesNotMatchReservation() throws Exception {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();
		Booking booking = createBooking(guest, roomType, null, null);
		LocalDate today = LocalDate.now(HOTEL_ZONE);
		booking.setCheckIn(today.plusDays(3));
		booking.setCheckOut(today.plusDays(5));
		booking.setStatus(BookingStatus.confirmed);
		booking = bookingRepository.saveAndFlush(booking);

		mockMvc.perform(post(REGISTER_PATH)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
							"code", booking.getConfirmationCode(),
							"email", "other.person@aurora.test",
							"password", "new-password-123"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", is("Email does not match the reservation guest")));
		assertThat(guestCredentialRepository.findByGuestId(guest.getId())).isEmpty();
	}

	private GuestLoginResponse login(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post(LOGIN_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(email, password)))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readValue(result.getResponse().getContentAsString(), GuestLoginResponse.class);
	}

	private String loginBody(String email, String password) throws Exception {
		return objectMapper.writeValueAsString(new GuestLoginRequest(email, password));
	}

	private static String uniqueEmail(String prefix) {
		return prefix + "." + uniqueSuffix() + "@aurora.test";
	}

	/** Estancia en curso: entró ayer y sale en dos días, siempre relativo a hoy. */
	private Booking createActiveBookingFor(Guest guest) {
		LocalDate today = LocalDate.now(HOTEL_ZONE);
		return createCheckedInBookingFor(guest, today.minusDays(1), today.plusDays(2));
	}

	private Booking createCheckedInBookingFor(Guest guest, LocalDate checkIn, LocalDate checkOut) {
		RoomType roomType = createRoomType();
		Booking booking = createBooking(guest, roomType, createRoom(roomType), createRate(roomType));
		booking.setCheckIn(checkIn);
		booking.setCheckOut(checkOut);
		booking.setStatus(BookingStatus.checked_in);
		// El backend normaliza el código de enlace a mayúsculas; los reales ya lo están.
		booking.setGuestLinkCode(booking.getGuestLinkCode().toUpperCase());
		return bookingRepository.save(booking);
	}

	private GuestCredential createCredential(Guest guest, String email, String rawPassword, boolean active) {
		GuestCredential credential = new GuestCredential();
		credential.setGuest(guest);
		credential.setEmail(email);
		credential.setPasswordHash(passwordEncoder.encode(rawPassword));
		credential.setActive(active);
		credential.setCreatedAt(now());
		credential.setUpdatedAt(now());
		credential = guestCredentialRepository.save(credential);
		testCredentialIds.add(credential.getId());
		return credential;
	}
}
