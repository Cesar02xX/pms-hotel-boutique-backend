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
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Deposit;
import com.aurora.pms.model.Role;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.DepositStatus;
import com.aurora.pms.model.enums.PaymentStatus;
import com.aurora.pms.model.enums.UserStatus;
import com.aurora.pms.repository.DepositRepository;
import com.aurora.pms.repository.PaymentRepository;
import com.aurora.pms.repository.RoleRepository;
import com.aurora.pms.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

class PaymentDepositControllerTest extends AbstractCatalogApiTest {

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private DepositRepository depositRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	private final List<UUID> moneyBookingIds = new ArrayList<>();
	private final List<UUID> userIds = new ArrayList<>();
	private final List<UUID> roleIds = new ArrayList<>();

	/** Corre antes del cleanup de la clase base, que borra las reservas. */
	@AfterEach
	void cleanUpMoneyData() {
		moneyBookingIds.forEach(bookingId -> {
			paymentRepository.deleteAll(paymentRepository.findByBookingIdOrderByCreatedAtAsc(bookingId));
			depositRepository.deleteAll(depositRepository.findByBookingIdOrderByCollectedAtAscCreatedAtAsc(bookingId));
		});
		userRepository.deleteAllById(userIds);
		roleRepository.deleteAllById(roleIds);
	}

	// ---------- Payments

	@Test
	void createPaymentReturnsCreatedAndIgnoresServerFields() throws Exception {
		Booking booking = createMoneyBooking();

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"amountCents": 50000, "method": "credit_card", "transactionReference": " AUTH-123 ",
								 "id": "%s", "status": "failed", "currency": "USD",
								 "paidAt": "2000-01-01T00:00:00Z", "createdAt": "2000-01-01T00:00:00Z",
								 "processedByUserId": "%s"}
								""".formatted(UUID.randomUUID(), UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.bookingId").value(booking.getId().toString()))
				.andExpect(jsonPath("$.amountCents").value(50000))
				.andExpect(jsonPath("$.currency").value("GTQ"))
				.andExpect(jsonPath("$.method").value("credit_card"))
				.andExpect(jsonPath("$.status").value("completed"))
				.andExpect(jsonPath("$.transactionReference").value("AUTH-123"))
				.andExpect(jsonPath("$.paidAt").value(not("2000-01-01T00:00:00Z")))
				.andExpect(jsonPath("$.createdAt").value(not("2000-01-01T00:00:00Z")))
				.andExpect(jsonPath("$.processedByUserId").value(nullValue()));

		assertThat(paymentRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId()))
				.singleElement()
				.satisfies(payment -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.completed));
	}

	@Test
	void createPaymentRecordsAuthenticatedUser() throws Exception {
		Booking booking = createMoneyBooking();
		User staff = createStaffUser();

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments", booking.getId())
						.with(user(staff.getEmail()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentBody(1000L)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.processedByUserId").value(staff.getId().toString()));
	}

	@Test
	void listPaymentsReturnsPaymentsOfBookingOnly() throws Exception {
		Booking booking = createMoneyBooking();
		Booking otherBooking = createMoneyBooking();
		String first = createPayment(booking, 1000L);
		String second = createPayment(booking, 2500L);
		createPayment(otherBooking, 9900L);

		mockMvc.perform(get("/api/v1/bookings/{bookingId}/payments", booking.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id").value(first))
				.andExpect(jsonPath("$[1].id").value(second));
	}

	@Test
	void invalidPaymentAmountsReturnBadRequest() throws Exception {
		Booking booking = createMoneyBooking();

		for (String amount : List.of("0", "-100", "null")) {
			mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments", booking.getId())
							.with(staffUser())
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"amountCents": %s, "method": "cash"}
									""".formatted(amount)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.amountCents").exists());
		}

		assertThat(paymentRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId())).isEmpty();
	}

	@Test
	void paymentWithoutMethodReturnsBadRequest() throws Exception {
		Booking booking = createMoneyBooking();

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"amountCents": 1000}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.method").exists());
	}

	@Test
	void paymentWithInvalidMethodReturnsBadRequest() throws Exception {
		Booking booking = createMoneyBooking();

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"amountCents": 1000, "method": "bitcoin"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void paymentOnMissingBookingReturnsNotFound() throws Exception {
		UUID bookingId = UUID.randomUUID();

		mockMvc.perform(get("/api/v1/bookings/{bookingId}/payments", bookingId).with(staffUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Booking not found")));
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments", bookingId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentBody(1000L)))
				.andExpect(status().isNotFound());
	}

	// ---------- Deposits

	@Test
	void createDepositIsHeldAndLinkedToPrimaryGuest() throws Exception {
		Booking booking = createMoneyBooking();

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/deposits", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"amountCents": 20000, "method": "cash", "notes": " Garantia ",
								 "guestId": "%s", "status": "refunded", "currency": "USD",
								 "collectedAt": "2000-01-01T00:00:00Z", "refundedAt": "2000-01-01T00:00:00Z"}
								""".formatted(UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.bookingId").value(booking.getId().toString()))
				.andExpect(jsonPath("$.guestId").value(booking.getGuest().getId().toString()))
				.andExpect(jsonPath("$.amountCents").value(20000))
				.andExpect(jsonPath("$.currency").value("GTQ"))
				.andExpect(jsonPath("$.method").value("cash"))
				.andExpect(jsonPath("$.status").value("held"))
				.andExpect(jsonPath("$.notes").value("Garantia"))
				.andExpect(jsonPath("$.collectedAt").value(not("2000-01-01T00:00:00Z")))
				.andExpect(jsonPath("$.refundedAt").value(nullValue()));
	}

	@Test
	void listDepositsReturnsDepositsOfBookingOnly() throws Exception {
		Booking booking = createMoneyBooking();
		Booking otherBooking = createMoneyBooking();
		String depositId = createDeposit(booking, 20000L);
		createDeposit(otherBooking, 5000L);

		mockMvc.perform(get("/api/v1/bookings/{bookingId}/deposits", booking.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(depositId));
	}

	@Test
	void invalidDepositRequestReturnsBadRequest() throws Exception {
		Booking booking = createMoneyBooking();

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/deposits", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"amountCents": 0}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.amountCents").exists())
				.andExpect(jsonPath("$.errors.method").exists());

		// "online" existe para pagos, pero no es un método de depósito.
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/deposits", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"amountCents": 1000, "method": "online"}
								"""))
				.andExpect(status().isBadRequest());

		assertThat(depositRepository.findByBookingIdOrderByCollectedAtAscCreatedAtAsc(booking.getId())).isEmpty();
	}

	@Test
	void refundDepositMarksItRefundedAndAppendsReason() throws Exception {
		Booking booking = createMoneyBooking();
		String depositId = createDeposit(booking, 20000L);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/deposits/{depositId}/refund", booking.getId(), depositId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason": " Salida sin danos "}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("refunded"))
				.andExpect(jsonPath("$.refundedAt").exists())
				.andExpect(jsonPath("$.notes").value("Garantia\nRefund: Salida sin danos"));
	}

	@Test
	void refundDepositWithoutBodyIsAllowed() throws Exception {
		Booking booking = createMoneyBooking();
		String depositId = createDeposit(booking, 20000L);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/deposits/{depositId}/refund", booking.getId(), depositId)
						.with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("refunded"))
				.andExpect(jsonPath("$.notes").value("Garantia"));
	}

	@Test
	void refundingTwiceReturnsBadRequest() throws Exception {
		Booking booking = createMoneyBooking();
		String depositId = createDeposit(booking, 20000L);
		refundDeposit(booking, depositId);
		Deposit refunded = depositRepository.findById(UUID.fromString(depositId)).orElseThrow();

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/deposits/{depositId}/refund", booking.getId(), depositId)
						.with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Deposit is already refunded"));

		Deposit after = depositRepository.findById(UUID.fromString(depositId)).orElseThrow();
		assertThat(after.getRefundedAt()).isEqualTo(refunded.getRefundedAt());
	}

	@Test
	void refundingAppliedDepositReturnsBadRequest() throws Exception {
		Booking booking = createMoneyBooking();
		String depositId = createDeposit(booking, 20000L);
		Deposit deposit = depositRepository.findById(UUID.fromString(depositId)).orElseThrow();
		deposit.setStatus(DepositStatus.applied);
		depositRepository.save(deposit);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/deposits/{depositId}/refund", booking.getId(), depositId)
						.with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Only held deposits can be refunded"));
	}

	@Test
	void refundingDepositOfAnotherBookingReturnsNotFound() throws Exception {
		Booking booking = createMoneyBooking();
		Booking otherBooking = createMoneyBooking();
		String depositId = createDeposit(booking, 20000L);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/deposits/{depositId}/refund",
						otherBooking.getId(), depositId).with(staffUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Deposit not found")));

		assertThat(depositRepository.findById(UUID.fromString(depositId)))
				.get().extracting(Deposit::getStatus).isEqualTo(DepositStatus.held);
	}

	@Test
	void depositOnMissingBookingReturnsNotFound() throws Exception {
		UUID bookingId = UUID.randomUUID();

		mockMvc.perform(get("/api/v1/bookings/{bookingId}/deposits", bookingId).with(staffUser()))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/deposits", bookingId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(depositBody(1000L)))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/deposits/{depositId}/refund", bookingId, UUID.randomUUID())
						.with(staffUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Booking not found")));
	}

	@Test
	void invalidUuidReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/bookings/{bookingId}/payments", "BKG-001").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/deposits/{depositId}/refund", UUID.randomUUID(), "DEP-1")
						.with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	// ---------- Security

	static Stream<Arguments> moneyEndpoints() {
		String id = UUID.randomUUID().toString();
		return Stream.of(
				Arguments.of(HttpMethod.GET, "/api/v1/bookings/" + id + "/payments"),
				Arguments.of(HttpMethod.POST, "/api/v1/bookings/" + id + "/payments"),
				Arguments.of(HttpMethod.GET, "/api/v1/bookings/" + id + "/deposits"),
				Arguments.of(HttpMethod.POST, "/api/v1/bookings/" + id + "/deposits"),
				Arguments.of(HttpMethod.POST, "/api/v1/bookings/" + id + "/deposits/" + id + "/refund")
		);
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("moneyEndpoints")
	void endpointWithoutTokenReturnsUnauthorized(HttpMethod method, String path) throws Exception {
		mockMvc.perform(request(method, path)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("moneyEndpoints")
	void endpointWithInvalidTokenReturnsUnauthorized(HttpMethod method, String path) throws Exception {
		mockMvc.perform(request(method, path)
						.header("Authorization", "Bearer invalid.jwt.token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized());
	}

	// ---------- Helpers

	private Booking createMoneyBooking() {
		RoomType roomType = createRoomType();
		Booking booking = createBooking(createGuest(), roomType, createRoom(roomType), createRate(roomType));
		moneyBookingIds.add(booking.getId());
		return booking;
	}

	private String createPayment(Booking booking, long amountCents) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentBody(amountCents)))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private String createDeposit(Booking booking, long amountCents) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/bookings/{bookingId}/deposits", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(depositBody(amountCents)))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private void refundDeposit(Booking booking, String depositId) throws Exception {
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/deposits/{depositId}/refund", booking.getId(), depositId)
						.with(staffUser()))
				.andExpect(status().isOk());
	}

	private User createStaffUser() {
		Role role = new Role();
		role.setCode("payments_" + uniqueSuffix());
		role.setName("Payments tester");
		role.setCreatedAt(now());
		role.setUpdatedAt(now());
		role = roleRepository.save(role);
		roleIds.add(role.getId());

		User staff = new User();
		staff.setFirstName("Payments");
		staff.setLastName("Tester");
		staff.setEmail("payments.%s@aurora.test".formatted(uniqueSuffix()));
		staff.setPasswordHash("not-used");
		staff.setRole(role);
		staff.setStatus(UserStatus.active);
		staff.setCreatedAt(now());
		staff.setUpdatedAt(now());
		staff = userRepository.save(staff);
		userIds.add(staff.getId());
		return staff;
	}

	private static String paymentBody(long amountCents) {
		return """
				{"amountCents": %d, "method": "cash"}
				""".formatted(amountCents);
	}

	private static String depositBody(long amountCents) {
		return """
				{"amountCents": %d, "method": "cash", "notes": "Garantia"}
				""".formatted(amountCents);
	}
}
