package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.GuestAccount;
import com.aurora.pms.model.Product;
import com.aurora.pms.model.Role;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.ChargeStatus;
import com.aurora.pms.model.enums.GuestAccountStatus;
import com.aurora.pms.model.enums.ProductCategory;
import com.aurora.pms.model.enums.UserStatus;
import com.aurora.pms.repository.ChargeRepository;
import com.aurora.pms.repository.GuestAccountRepository;
import com.aurora.pms.repository.ProductRepository;
import com.aurora.pms.repository.RoleRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.security.SecurityPermissions;
import com.jayway.jsonpath.JsonPath;

class GuestFolioControllerTest extends AbstractCatalogApiTest {

	@Autowired
	private GuestAccountRepository guestAccountRepository;

	@Autowired
	private ChargeRepository chargeRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	private final List<UUID> folioBookingIds = new ArrayList<>();
	private final List<UUID> productIds = new ArrayList<>();
	private final List<UUID> userIds = new ArrayList<>();
	private final List<UUID> roleIds = new ArrayList<>();

	/** Corre antes del cleanup de la clase base, que borra las reservas. */
	@AfterEach
	void cleanUpFolioData() {
		folioBookingIds.forEach(bookingId -> {
			chargeRepository.deleteAll(chargeRepository.findByBookingIdOrderByChargedAtAscCreatedAtAsc(bookingId));
			guestAccountRepository.findByBookingId(bookingId).ifPresent(guestAccountRepository::delete);
		});
		productRepository.deleteAllById(productIds);
		userRepository.deleteAllById(userIds);
		roleRepository.deleteAllById(roleIds);
	}

	@Test
	void openFolioCreatesAccountLinkedToPrimaryGuest() throws Exception {
		Booking booking = createFolioBooking();

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/folio/open", booking.getId()).with(staffUser()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accountId").exists())
				.andExpect(jsonPath("$.bookingId").value(booking.getId().toString()))
				.andExpect(jsonPath("$.guestId").value(booking.getGuest().getId().toString()))
				.andExpect(jsonPath("$.status").value("open"))
				.andExpect(jsonPath("$.balanceCents").value(0))
				.andExpect(jsonPath("$.currency").value("GTQ"))
				.andExpect(jsonPath("$.openedAt").exists())
				.andExpect(jsonPath("$.closedAt").value(nullValue()))
				.andExpect(jsonPath("$.charges", hasSize(0)));

		assertThat(guestAccountRepository.findByBookingId(booking.getId())).isPresent();
	}

	@Test
	void reopeningFolioIsIdempotent() throws Exception {
		Booking booking = createFolioBooking();
		String accountId = openFolio(booking);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/folio/open", booking.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountId").value(accountId));

		assertThat(guestAccountRepository.findAll().stream()
				.filter(account -> account.getBooking().getId().equals(booking.getId()))
				.count()).isEqualTo(1);
	}

	@ParameterizedTest
	@EnumSource(value = BookingStatus.class, names = {"checked_out", "cancelled", "no_show"})
	void openFolioForTerminalBookingStatusReturnsBadRequest(BookingStatus status) throws Exception {
		Booking booking = createFolioBooking();
		booking.setStatus(status);
		bookingRepository.save(booking);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/folio/open", booking.getId()).with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void getFolioReturnsAccountWithChargesAndTotals() throws Exception {
		Booking booking = createFolioBooking();
		String accountId = openFolio(booking);
		postCharge(booking, 2, 1500L);
		String voidedId = postCharge(booking, 1, 1000L);
		voidCharge(booking, voidedId, "Error de registro");

		mockMvc.perform(get("/api/v1/bookings/{bookingId}/folio", booking.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountId").value(accountId))
				.andExpect(jsonPath("$.balanceCents").value(3000))
				.andExpect(jsonPath("$.activeChargesCents").value(3000))
				.andExpect(jsonPath("$.voidedChargesCents").value(1000))
				.andExpect(jsonPath("$.charges", hasSize(2)));
	}

	@Test
	void getFolioWithoutAccountReturnsNotFound() throws Exception {
		Booking booking = createFolioBooking();

		mockMvc.perform(get("/api/v1/bookings/{bookingId}/folio", booking.getId()).with(staffUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Guest account not found")));
	}

	@Test
	void createChargeCalculatesAmountAndIgnoresServerFields() throws Exception {
		Booking booking = createFolioBooking();
		openFolio(booking);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description": " Minibar ", "quantity": 3, "unitPriceCents": 2500,
								 "category": "consumption", "amountCents": 1, "status": "voided",
								 "currency": "USD", "chargedAt": "2000-01-01T00:00:00Z"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.bookingId").value(booking.getId().toString()))
				.andExpect(jsonPath("$.description").value("Minibar"))
				.andExpect(jsonPath("$.quantity").value(3))
				.andExpect(jsonPath("$.unitPriceCents").value(2500))
				.andExpect(jsonPath("$.amountCents").value(7500))
				.andExpect(jsonPath("$.currency").value("GTQ"))
				.andExpect(jsonPath("$.category").value("consumption"))
				.andExpect(jsonPath("$.status").value("posted"))
				.andExpect(jsonPath("$.chargedAt").value(not("2000-01-01T00:00:00Z")));

		assertThat(currentAccount(booking).getBalanceCents()).isEqualTo(7500L);
	}

	@Test
	void createChargeIncrementsBalance() throws Exception {
		Booking booking = createFolioBooking();
		openFolio(booking);

		postCharge(booking, 2, 45000L);
		postCharge(booking, 1, 1250L);

		assertThat(currentAccount(booking).getBalanceCents()).isEqualTo(91250L);
	}

	@Test
	void createChargeWithZeroAmountReturnsBadRequest() throws Exception {
		Booking booking = createFolioBooking();
		openFolio(booking);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description": "Cortesia", "quantity": 1, "unitPriceCents": 0, "category": "stay"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Charge amount must be greater than zero"));

		assertThat(currentAccount(booking).getBalanceCents()).isZero();
	}

	@Test
	void createChargeRecordsProductAndAuthenticatedUser() throws Exception {
		Booking booking = createFolioBooking();
		openFolio(booking);
		Product product = createProduct();
		User staff = createStaffUser();

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges", booking.getId())
						.with(userWithPermissions(staff.getEmail(), SecurityPermissions.CHARGES_WRITE))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description": "Agua", "quantity": 1, "unitPriceCents": 1500,
								 "category": "consumption", "productId": "%s",
								 "createdByUserId": "%s"}
								""".formatted(product.getId(), UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.productId").value(product.getId().toString()))
				.andExpect(jsonPath("$.createdByUserId").value(staff.getId().toString()));
	}

	@Test
	void createChargeWithUnknownProductReturnsNotFound() throws Exception {
		Booking booking = createFolioBooking();
		openFolio(booking);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description": "Agua", "quantity": 1, "unitPriceCents": 1500,
								 "category": "consumption", "productId": "%s"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isNotFound());
	}

	@Test
	void createChargeWithoutOpenFolioReturnsNotFound() throws Exception {
		Booking booking = createFolioBooking();

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(chargeBody(1, 1000L)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Guest account not found")));
	}

	@Test
	void createChargeOnClosedFolioReturnsBadRequest() throws Exception {
		Booking booking = createFolioBooking();
		openFolio(booking);
		GuestAccount account = currentAccount(booking);
		account.setStatus(GuestAccountStatus.closed);
		account.setClosedAt(now());
		guestAccountRepository.save(account);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(chargeBody(1, 1000L)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createChargeWithInvalidFieldsReturnsBadRequest() throws Exception {
		Booking booking = createFolioBooking();
		openFolio(booking);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description": " ", "quantity": 0, "unitPriceCents": -1}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.description").exists())
				.andExpect(jsonPath("$.errors.quantity").exists())
				.andExpect(jsonPath("$.errors.unitPriceCents").exists())
				.andExpect(jsonPath("$.errors.category").exists());

		assertThat(currentAccount(booking).getBalanceCents()).isZero();
	}

	@Test
	void createChargeWithOverflowingAmountReturnsBadRequest() throws Exception {
		Booking booking = createFolioBooking();
		openFolio(booking);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(chargeBody(2, Long.MAX_VALUE)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createChargeWithInvalidCategoryReturnsBadRequest() throws Exception {
		Booking booking = createFolioBooking();
		openFolio(booking);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description": "Spa", "quantity": 1, "unitPriceCents": 1000, "category": "spa"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void listChargesReturnsActiveAndVoidedCharges() throws Exception {
		Booking booking = createFolioBooking();
		openFolio(booking);
		postCharge(booking, 1, 1000L);
		String voidedId = postCharge(booking, 1, 500L);
		voidCharge(booking, voidedId, "Duplicado");

		mockMvc.perform(get("/api/v1/bookings/{bookingId}/charges", booking.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[1].id").value(voidedId))
				.andExpect(jsonPath("$[1].status").value("voided"))
				.andExpect(jsonPath("$[1].voidReason").value("Duplicado"));
	}

	@Test
	void voidChargeRevertsBalance() throws Exception {
		Booking booking = createFolioBooking();
		openFolio(booking);
		postCharge(booking, 1, 4000L);
		String chargeId = postCharge(booking, 2, 1500L);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges/{chargeId}/void", booking.getId(), chargeId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": "  Cargo equivocado  "}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("voided"))
				.andExpect(jsonPath("$.voidReason").value("Cargo equivocado"))
				.andExpect(jsonPath("$.amountCents").value(3000));

		assertThat(currentAccount(booking).getBalanceCents()).isEqualTo(4000L);
		assertThat(chargeRepository.findById(UUID.fromString(chargeId)))
				.get().extracting(charge -> charge.getStatus()).isEqualTo(ChargeStatus.voided);
	}

	@Test
	void voidingTwiceReturnsBadRequestAndKeepsBalance() throws Exception {
		Booking booking = createFolioBooking();
		openFolio(booking);
		String chargeId = postCharge(booking, 1, 2000L);
		voidCharge(booking, chargeId, "Primer intento");

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges/{chargeId}/void", booking.getId(), chargeId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": "Segundo intento"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Charge is already voided"));

		assertThat(currentAccount(booking).getBalanceCents()).isZero();
	}

	@Test
	void voidWithoutReasonReturnsBadRequest() throws Exception {
		Booking booking = createFolioBooking();
		openFolio(booking);
		String chargeId = postCharge(booking, 1, 2000L);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges/{chargeId}/void", booking.getId(), chargeId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": " "}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.reason").exists());

		assertThat(currentAccount(booking).getBalanceCents()).isEqualTo(2000L);
	}

	@Test
	void voidChargeFromAnotherBookingReturnsNotFound() throws Exception {
		Booking firstBooking = createFolioBooking();
		Booking secondBooking = createFolioBooking();
		openFolio(firstBooking);
		openFolio(secondBooking);
		String chargeId = postCharge(firstBooking, 1, 2000L);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges/{chargeId}/void", secondBooking.getId(), chargeId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": "Otra reserva"}
								"""))
				.andExpect(status().isNotFound());

		assertThat(currentAccount(firstBooking).getBalanceCents()).isEqualTo(2000L);
	}

	@Test
	void missingBookingReturnsNotFound() throws Exception {
		UUID bookingId = UUID.randomUUID();

		mockMvc.perform(get("/api/v1/bookings/{bookingId}/folio", bookingId).with(staffUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Booking not found")));
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/folio/open", bookingId).with(staffUser()))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/bookings/{bookingId}/charges", bookingId).with(staffUser()))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges", bookingId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(chargeBody(1, 1000L)))
				.andExpect(status().isNotFound());
	}

	@Test
	void invalidUuidReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/bookings/{bookingId}/folio", "BKG-001").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges/{chargeId}/void", UUID.randomUUID(), "CHG-1")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": "x"}
								"""))
				.andExpect(status().isBadRequest());
	}

	static Stream<Arguments> folioEndpoints() {
		String id = UUID.randomUUID().toString();
		return Stream.of(
				Arguments.of(HttpMethod.GET, "/api/v1/bookings/" + id + "/folio"),
				Arguments.of(HttpMethod.POST, "/api/v1/bookings/" + id + "/folio/open"),
				Arguments.of(HttpMethod.GET, "/api/v1/bookings/" + id + "/charges"),
				Arguments.of(HttpMethod.POST, "/api/v1/bookings/" + id + "/charges"),
				Arguments.of(HttpMethod.POST, "/api/v1/bookings/" + id + "/charges/" + id + "/void")
		);
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("folioEndpoints")
	void endpointWithoutTokenReturnsUnauthorized(HttpMethod method, String path) throws Exception {
		mockMvc.perform(request(method, path)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("folioEndpoints")
	void endpointWithInvalidTokenReturnsUnauthorized(HttpMethod method, String path) throws Exception {
		mockMvc.perform(request(method, path)
						.header("Authorization", "Bearer invalid.jwt.token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized());
	}

	private Booking createFolioBooking() {
		var roomType = createRoomType();
		Booking booking = createBooking(createGuest(), roomType, createRoom(roomType), createRate(roomType));
		folioBookingIds.add(booking.getId());
		return booking;
	}

	private String openFolio(Booking booking) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/bookings/{bookingId}/folio/open", booking.getId())
						.with(staffUser()))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.accountId");
	}

	private String postCharge(Booking booking, int quantity, long unitPriceCents) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(chargeBody(quantity, unitPriceCents)))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private void voidCharge(Booking booking, String chargeId, String reason) throws Exception {
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges/{chargeId}/void", booking.getId(), chargeId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"reason\": \"%s\"}".formatted(reason)))
				.andExpect(status().isOk());
	}

	private GuestAccount currentAccount(Booking booking) {
		return guestAccountRepository.findByBookingId(booking.getId()).orElseThrow();
	}

	private Product createProduct() {
		Product product = new Product();
		product.setSku("SKU-" + uniqueSuffix());
		product.setName("Agua mineral");
		product.setCategory(ProductCategory.minibar);
		product.setPriceCents(1500L);
		product.setCreatedAt(now());
		product.setUpdatedAt(now());
		product = productRepository.save(product);
		productIds.add(product.getId());
		return product;
	}

	private User createStaffUser() {
		Role role = new Role();
		role.setCode("folio_" + uniqueSuffix());
		role.setName("Folio tester");
		role.setCreatedAt(now());
		role.setUpdatedAt(now());
		role = roleRepository.save(role);
		roleIds.add(role.getId());

		User staff = new User();
		staff.setFirstName("Folio");
		staff.setLastName("Tester");
		staff.setEmail("folio.%s@aurora.test".formatted(uniqueSuffix()));
		staff.setPasswordHash("not-used");
		staff.setRole(role);
		staff.setStatus(UserStatus.active);
		staff.setCreatedAt(now());
		staff.setUpdatedAt(now());
		staff = userRepository.save(staff);
		userIds.add(staff.getId());
		return staff;
	}

	private static String chargeBody(int quantity, long unitPriceCents) {
		return """
				{"description": "Consumo", "quantity": %d, "unitPriceCents": %d, "category": "consumption"}
				""".formatted(quantity, unitPriceCents);
	}
}
