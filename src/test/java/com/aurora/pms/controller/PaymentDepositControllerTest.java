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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MvcResult;

import com.aurora.pms.dto.request.CreatePaymentRequest;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Deposit;
import com.aurora.pms.model.GuestAccount;
import com.aurora.pms.model.Role;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.DepositStatus;
import com.aurora.pms.model.enums.GuestAccountStatus;
import com.aurora.pms.model.enums.PaymentMethod;
import com.aurora.pms.model.enums.PaymentStatus;
import com.aurora.pms.model.enums.UserStatus;
import com.aurora.pms.repository.ChargeRepository;
import com.aurora.pms.repository.DepositRepository;
import com.aurora.pms.repository.GuestAccountRepository;
import com.aurora.pms.repository.PaymentRepository;
import com.aurora.pms.repository.RoleRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.security.SecurityPermissions;
import com.aurora.pms.service.GuestFolioService;
import com.aurora.pms.service.PaymentService;
import com.jayway.jsonpath.JsonPath;

class PaymentDepositControllerTest extends AbstractCatalogApiTest {

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private DepositRepository depositRepository;

	@Autowired
	private ChargeRepository chargeRepository;

	@Autowired
	private GuestAccountRepository guestAccountRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private GuestFolioService folioService;

	@Autowired
	private PaymentService paymentService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<UUID> moneyBookingIds = new ArrayList<>();
	private final List<UUID> userIds = new ArrayList<>();
	private final List<UUID> roleIds = new ArrayList<>();

	/** Corre antes del cleanup de la clase base, que borra las reservas. */
	@AfterEach
	void cleanUpMoneyData() {
		moneyBookingIds.forEach(bookingId -> {
			paymentRepository.deleteAll(paymentRepository.findByBookingIdOrderByCreatedAtAsc(bookingId));
			depositRepository.deleteAll(depositRepository.findByBookingIdOrderByCollectedAtAscCreatedAtAsc(bookingId));
			chargeRepository.deleteAll(chargeRepository.findByBookingIdOrderByChargedAtAscCreatedAtAsc(bookingId));
			guestAccountRepository.findByBookingId(bookingId).ifPresent(guestAccountRepository::delete);
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
						.with(userWithPermissions(staff.getEmail(), SecurityPermissions.PAYMENTS_WRITE))
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
	void decimalAmountsAreRejectedInsteadOfTruncated() throws Exception {
		Booking booking = createMoneyBooking();

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"amountCents": 1.5, "method": "cash"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/deposits", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"amountCents": 20000.99, "method": "cash"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));

		assertThat(paymentRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId())).isEmpty();
		assertThat(depositRepository.findByBookingIdOrderByCollectedAtAscCreatedAtAsc(booking.getId())).isEmpty();
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

	// ---------- Integración con el folio

	@Test
	void paymentReducesOpenFolioBalance() throws Exception {
		Booking booking = createMoneyBooking();
		openFolio(booking);
		postCharge(booking, 10000L);

		createPayment(booking, 4000L);

		mockMvc.perform(get("/api/v1/bookings/{bookingId}/folio", booking.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balanceCents").value(6000))
				.andExpect(jsonPath("$.activeChargesCents").value(10000))
				.andExpect(jsonPath("$.completedPaymentsCents").value(4000));
	}

	@Test
	void paymentBeforeFolioIsCountedWhenFolioOpens() throws Exception {
		Booking booking = createMoneyBooking();
		createPayment(booking, 3000L);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/folio/open", booking.getId()).with(staffUser()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.balanceCents").value(-3000))
				.andExpect(jsonPath("$.completedPaymentsCents").value(3000));

		postCharge(booking, 5000L);
		assertThat(currentAccount(booking).getBalanceCents()).isEqualTo(2000L);
	}

	@Test
	void concurrentPaymentDuringFolioOpenWaitsAndUpdatesOpenedFolioBalance() throws Exception {
		Booking booking = createMoneyBooking();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		CountDownLatch paymentStarted = new CountDownLatch(1);

		try {
			Future<?> payment = new TransactionTemplate(transactionManager).execute(status -> {
				bookingRepository.findByIdForUpdate(booking.getId()).orElseThrow();

				Future<?> future = executor.submit(() -> {
					paymentStarted.countDown();
					paymentService.create(
							booking.getId(),
							new CreatePaymentRequest(5000L, PaymentMethod.cash, null),
							null
					);
				});

				awaitPaymentStart(paymentStarted);
				waitBrieflyForUnlockedPaymentToFinish(future);
				assertThat(future.isDone()).isFalse();

				folioService.openFolio(booking.getId());
				assertThat(currentAccount(booking).getBalanceCents()).isZero();
				return future;
			});

			payment.get(5, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}

		assertThat(currentAccount(booking).getBalanceCents()).isEqualTo(-5000L);
		assertThat(paymentRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId()))
				.singleElement()
				.satisfies(payment -> assertThat(payment.getAmountCents()).isEqualTo(5000L));
	}

	@Test
	void paymentOnClosedFolioReturnsBadRequestAndIsNotSaved() throws Exception {
		Booking booking = createMoneyBooking();
		openFolio(booking);
		GuestAccount account = currentAccount(booking);
		account.setStatus(GuestAccountStatus.closed);
		account.setClosedAt(now());
		guestAccountRepository.save(account);

		mockMvc.perform(post("/api/v1/bookings/{bookingId}/payments", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(paymentBody(1000L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Guest account is not open"));

		assertThat(paymentRepository.findByBookingIdOrderByCreatedAtAsc(booking.getId())).isEmpty();
		assertThat(currentAccount(booking).getBalanceCents()).isZero();
	}

	@Test
	void depositsDoNotChangeFolioBalance() throws Exception {
		Booking booking = createMoneyBooking();
		openFolio(booking);
		postCharge(booking, 10000L);

		String depositId = createDeposit(booking, 20000L);
		assertThat(currentAccount(booking).getBalanceCents()).isEqualTo(10000L);

		refundDeposit(booking, depositId);
		assertThat(currentAccount(booking).getBalanceCents()).isEqualTo(10000L);
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

	private void openFolio(Booking booking) throws Exception {
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/folio/open", booking.getId()).with(staffUser()))
				.andExpect(status().isCreated());
	}

	private void postCharge(Booking booking, long unitPriceCents) throws Exception {
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/charges", booking.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"description": "Noche", "quantity": 1, "unitPriceCents": %d, "category": "stay"}
								""".formatted(unitPriceCents)))
				.andExpect(status().isCreated());
	}

	private GuestAccount currentAccount(Booking booking) {
		return guestAccountRepository.findByBookingId(booking.getId()).orElseThrow();
	}

	private static void awaitPaymentStart(CountDownLatch paymentStarted) {
		try {
			assertThat(paymentStarted.await(2, TimeUnit.SECONDS)).isTrue();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError("Interrupted while waiting for concurrent payment", exception);
		}
	}

	private static void waitBrieflyForUnlockedPaymentToFinish(Future<?> payment) {
		try {
			for (int i = 0; i < 10 && !payment.isDone(); i++) {
				TimeUnit.MILLISECONDS.sleep(50);
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError("Interrupted while checking concurrent payment", exception);
		}
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
