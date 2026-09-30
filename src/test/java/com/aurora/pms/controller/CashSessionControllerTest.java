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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.aurora.pms.dto.request.OpenCashSessionRequest;
import com.aurora.pms.dto.response.CashSessionResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.model.CashSession;
import com.aurora.pms.model.Role;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.CashSessionStatus;
import com.aurora.pms.model.enums.UserStatus;
import com.aurora.pms.repository.CashMovementRepository;
import com.aurora.pms.repository.CashSessionRepository;
import com.aurora.pms.repository.RoleRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.service.CashSessionService;
import com.jayway.jsonpath.JsonPath;

class CashSessionControllerTest extends AbstractCatalogApiTest {

	@Autowired
	private CashSessionRepository cashSessionRepository;

	@Autowired
	private CashMovementRepository cashMovementRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private CashSessionService cashSessionService;

	private final List<UUID> sessionIds = new ArrayList<>();
	private final List<UUID> userIds = new ArrayList<>();
	private final List<UUID> roleIds = new ArrayList<>();
	private final List<UUID> preexistingOpenSessionIds = new ArrayList<>();

	private User cashier;

	/**
	 * Solo puede haber una caja abierta: si la BD local ya tiene una, se cierra
	 * temporalmente y se restaura al final para no alterar los datos del equipo.
	 */
	@BeforeEach
	void setUpCashData() {
		cashSessionRepository.findAll().stream()
				.filter(session -> session.getStatus() == CashSessionStatus.open)
				.forEach(session -> {
					preexistingOpenSessionIds.add(session.getId());
					session.setStatus(CashSessionStatus.closed);
					cashSessionRepository.save(session);
				});
		cashier = createStaffUser("Cashier");
	}

	@AfterEach
	void cleanUpCashData() {
		sessionIds.forEach(sessionId ->
				cashMovementRepository.deleteAll(
						cashMovementRepository.findByCashSessionIdOrderByOccurredAtAscCreatedAtAsc(sessionId)));
		cashSessionRepository.deleteAllById(sessionIds);
		userRepository.deleteAllById(userIds);
		roleRepository.deleteAllById(roleIds);
		cashSessionRepository.findAllById(preexistingOpenSessionIds).forEach(session -> {
			session.setStatus(CashSessionStatus.open);
			cashSessionRepository.save(session);
		});
	}

	// ---------- Open / current

	@Test
	void openReturnsCreatedAndIgnoresServerFields() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/cash-sessions/open")
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"openingBalanceCents": 50000, "notes": " Fondo inicial ",
								 "id": "%s", "status": "closed", "currency": "USD",
								 "openedAt": "2000-01-01T00:00:00Z", "openedByUserId": "%s",
								 "expectedBalanceCents": 1, "differenceCents": 1}
								""".formatted(UUID.randomUUID(), UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.openedByUserId").value(cashier.getId().toString()))
				.andExpect(jsonPath("$.openingBalanceCents").value(50000))
				.andExpect(jsonPath("$.currency").value("GTQ"))
				.andExpect(jsonPath("$.status").value("open"))
				.andExpect(jsonPath("$.openedAt").value(not("2000-01-01T00:00:00Z")))
				.andExpect(jsonPath("$.totalIncomeCents").value(0))
				.andExpect(jsonPath("$.totalExpenseCents").value(0))
				.andExpect(jsonPath("$.expectedBalanceCents").value(50000))
				.andExpect(jsonPath("$.closedAt").value(nullValue()))
				.andExpect(jsonPath("$.differenceCents").value(nullValue()))
				.andExpect(jsonPath("$.notes").value("Fondo inicial"))
				.andReturn();

		UUID id = trackSession(result);
		assertThat(cashSessionRepository.findById(id)).get()
				.satisfies(session -> assertThat(session.getStatus()).isEqualTo(CashSessionStatus.open));
	}

	@Test
	void openWithZeroOpeningBalanceIsAllowed() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/cash-sessions/open")
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(openBody(0L)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.openingBalanceCents").value(0))
				.andReturn();
		trackSession(result);
	}

	@Test
	void openWithUserMissingInDatabaseReturnsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/cash-sessions/open")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(openBody(1000L)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));

		assertThat(cashSessionRepository.existsByStatus(CashSessionStatus.open)).isFalse();
	}

	@Test
	void secondOpenReturnsBadRequestWhileSessionIsOpen() throws Exception {
		openSession(10000L);
		User otherCashier = createStaffUser("Other");

		mockMvc.perform(post("/api/v1/cash-sessions/open")
						.with(user(otherCashier.getEmail()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(openBody(5000L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("There is already an open cash session"));

		assertThat(countOpenSessions()).isEqualTo(1);
	}

	@Test
	void concurrentOpensCreateOnlyOneSession() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<CashSessionResponse> open = () -> {
			start.await(2, TimeUnit.SECONDS);
			return cashSessionService.open(new OpenCashSessionRequest(1000L, null), cashier.getEmail());
		};

		try {
			Future<CashSessionResponse> first = executor.submit(open);
			Future<CashSessionResponse> second = executor.submit(open);
			start.countDown();

			int opened = 0;
			int rejected = 0;
			for (Future<CashSessionResponse> future : List.of(first, second)) {
				try {
					sessionIds.add(future.get(10, TimeUnit.SECONDS).id());
					opened++;
				} catch (ExecutionException exception) {
					assertThat(exception.getCause()).isInstanceOf(BadRequestException.class);
					rejected++;
				}
			}

			assertThat(opened).isEqualTo(1);
			assertThat(rejected).isEqualTo(1);
			assertThat(countOpenSessions()).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void currentReturnsOpenSessionWithLiveTotals() throws Exception {
		String sessionId = openSession(10000L);
		createMovement(sessionId, "income", 2500L);
		createMovement(sessionId, "expense", 1000L);

		mockMvc.perform(get("/api/v1/cash-sessions/current").with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(sessionId))
				.andExpect(jsonPath("$.status").value("open"))
				.andExpect(jsonPath("$.totalIncomeCents").value(2500))
				.andExpect(jsonPath("$.totalExpenseCents").value(1000))
				.andExpect(jsonPath("$.expectedBalanceCents").value(11500))
				.andExpect(jsonPath("$.countedBalanceCents").value(nullValue()));
	}

	@Test
	void currentWithoutOpenSessionReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/cash-sessions/current").with(staffUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No open cash session"));
	}

	@Test
	void currentAfterCloseReturnsNotFound() throws Exception {
		String sessionId = openSession(1000L);
		closeSession(sessionId, 1000L);

		mockMvc.perform(get("/api/v1/cash-sessions/current").with(staffUser()))
				.andExpect(status().isNotFound());
	}

	// ---------- Movements

	@Test
	void createMovementReturnsCreatedAndIgnoresServerFields() throws Exception {
		String sessionId = openSession(10000L);

		mockMvc.perform(post("/api/v1/cash-sessions/{id}/movements", sessionId)
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"type": "income", "concept": " Venta minibar ", "amountCents": 3500,
								 "id": "%s", "currency": "USD", "occurredAt": "2000-01-01T00:00:00Z",
								 "responsibleUserId": "%s", "paymentId": "%s", "cashSessionId": "%s"}
								""".formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
								UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.cashSessionId").value(sessionId))
				.andExpect(jsonPath("$.type").value("income"))
				.andExpect(jsonPath("$.concept").value("Venta minibar"))
				.andExpect(jsonPath("$.amountCents").value(3500))
				.andExpect(jsonPath("$.currency").value("GTQ"))
				.andExpect(jsonPath("$.responsibleUserId").value(cashier.getId().toString()))
				.andExpect(jsonPath("$.occurredAt").value(not("2000-01-01T00:00:00Z")))
				.andExpect(jsonPath("$.paymentId").value(nullValue()));
	}

	@Test
	void listMovementsReturnsMovementsOfSessionInOrder() throws Exception {
		String sessionId = openSession(10000L);
		String first = createMovement(sessionId, "income", 1000L);
		String second = createMovement(sessionId, "expense", 500L);
		closeSession(sessionId, 10500L);
		String otherSessionId = openSession(0L);
		createMovement(otherSessionId, "income", 9900L);

		mockMvc.perform(get("/api/v1/cash-sessions/{id}/movements", sessionId).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id").value(first))
				.andExpect(jsonPath("$[1].id").value(second));
	}

	@Test
	void expenseCannotExceedAvailableCash() throws Exception {
		String sessionId = openSession(1000L);
		createMovement(sessionId, "income", 500L);

		mockMvc.perform(post("/api/v1/cash-sessions/{id}/movements", sessionId)
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody("expense", 1501L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith("Expense exceeds the cash available")));

		// Retirar exactamente lo disponible sí se permite.
		createMovement(sessionId, "expense", 1500L);
		mockMvc.perform(get("/api/v1/cash-sessions/current").with(staffUser()))
				.andExpect(jsonPath("$.expectedBalanceCents").value(0));
	}

	@Test
	void movementOnClosedSessionReturnsBadRequest() throws Exception {
		String sessionId = openSession(1000L);
		closeSession(sessionId, 1000L);

		mockMvc.perform(post("/api/v1/cash-sessions/{id}/movements", sessionId)
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody("income", 100L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Cannot register movements on a closed cash session"));

		assertThat(cashMovementRepository.findByCashSessionIdOrderByOccurredAtAscCreatedAtAsc(
				UUID.fromString(sessionId))).isEmpty();
	}

	@Test
	void invalidMovementBodiesReturnBadRequest() throws Exception {
		String sessionId = openSession(1000L);

		for (String amount : List.of("0", "-100", "null")) {
			mockMvc.perform(post("/api/v1/cash-sessions/{id}/movements", sessionId)
							.with(cashierUser())
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"type": "income", "concept": "Test", "amountCents": %s}
									""".formatted(amount)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.amountCents").exists());
		}
		mockMvc.perform(post("/api/v1/cash-sessions/{id}/movements", sessionId)
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"concept": "  ", "amountCents": 100}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.type").exists())
				.andExpect(jsonPath("$.errors.concept").exists());
		mockMvc.perform(post("/api/v1/cash-sessions/{id}/movements", sessionId)
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"type": "income", "concept": "Test", "amountCents": 1.5}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));

		assertThat(cashMovementRepository.findByCashSessionIdOrderByOccurredAtAscCreatedAtAsc(
				UUID.fromString(sessionId))).isEmpty();
	}

	@Test
	void invalidMovementTypeReturnsBadRequest() throws Exception {
		String sessionId = openSession(1000L);

		mockMvc.perform(post("/api/v1/cash-sessions/{id}/movements", sessionId)
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody("transfer", 100L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Malformed or invalid request body"));
	}

	// ---------- Close

	@Test
	void closeComputesTotalsInBackend() throws Exception {
		String sessionId = openSession(10000L, "Apertura");
		createMovement(sessionId, "income", 5000L);
		createMovement(sessionId, "income", 1000L);
		createMovement(sessionId, "expense", 4000L);
		User closer = createStaffUser("Closer");

		mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", sessionId)
						.with(user(closer.getEmail()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"countedBalanceCents": 11500, "notes": " Faltante ",
								 "expectedBalanceCents": 999999, "differenceCents": 0, "status": "open",
								 "closedAt": "2000-01-01T00:00:00Z"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("closed"))
				.andExpect(jsonPath("$.totalIncomeCents").value(6000))
				.andExpect(jsonPath("$.totalExpenseCents").value(4000))
				.andExpect(jsonPath("$.expectedBalanceCents").value(12000))
				.andExpect(jsonPath("$.countedBalanceCents").value(11500))
				.andExpect(jsonPath("$.differenceCents").value(-500))
				.andExpect(jsonPath("$.openedByUserId").value(cashier.getId().toString()))
				.andExpect(jsonPath("$.closedByUserId").value(closer.getId().toString()))
				.andExpect(jsonPath("$.closedAt").value(not("2000-01-01T00:00:00Z")))
				.andExpect(jsonPath("$.notes").value("Apertura\nFaltante"));

		CashSession stored = cashSessionRepository.findById(UUID.fromString(sessionId)).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(CashSessionStatus.closed);
		assertThat(stored.getExpectedBalanceCents()).isEqualTo(12000L);
		assertThat(stored.getCountedBalanceCents()).isEqualTo(11500L);
		assertThat(stored.getDifferenceCents()).isEqualTo(-500L);
		assertThat(stored.getClosedAt()).isNotNull();
	}

	@Test
	void closeWithSurplusReturnsPositiveDifference() throws Exception {
		String sessionId = openSession(2000L);

		mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", sessionId)
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(closeBody(2300L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.expectedBalanceCents").value(2000))
				.andExpect(jsonPath("$.differenceCents").value(300));
	}

	@Test
	void secondCloseReturnsBadRequest() throws Exception {
		String sessionId = openSession(1000L);
		closeSession(sessionId, 1000L);

		mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", sessionId)
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(closeBody(5000L)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Cash session is already closed"));

		assertThat(cashSessionRepository.findById(UUID.fromString(sessionId))).get()
				.satisfies(session -> assertThat(session.getCountedBalanceCents()).isEqualTo(1000L));
	}

	@Test
	void sessionCanBeReopenedAfterClose() throws Exception {
		String sessionId = openSession(1000L);
		closeSession(sessionId, 1000L);

		String nextSessionId = openSession(1000L);

		assertThat(nextSessionId).isNotEqualTo(sessionId);
	}

	@Test
	void invalidOpenAndCloseBodiesReturnBadRequest() throws Exception {
		for (String amount : List.of("-1", "null")) {
			mockMvc.perform(post("/api/v1/cash-sessions/open")
							.with(cashierUser())
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"openingBalanceCents": %s}
									""".formatted(amount)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.openingBalanceCents").exists());
		}
		mockMvc.perform(post("/api/v1/cash-sessions/open")
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"openingBalanceCents": 10.5}
								"""))
				.andExpect(status().isBadRequest());
		assertThat(countOpenSessions()).isZero();

		String sessionId = openSession(1000L);
		for (String amount : List.of("-1", "null")) {
			mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", sessionId)
							.with(cashierUser())
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"countedBalanceCents": %s}
									""".formatted(amount)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.countedBalanceCents").exists());
		}
		assertThat(cashSessionRepository.findById(UUID.fromString(sessionId))).get()
				.satisfies(session -> assertThat(session.getStatus()).isEqualTo(CashSessionStatus.open));
	}

	// ---------- Not found / invalid ids

	@Test
	void missingSessionReturnsNotFound() throws Exception {
		UUID id = UUID.randomUUID();

		mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", id)
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(closeBody(0L)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Cash session not found")));
		mockMvc.perform(get("/api/v1/cash-sessions/{id}/movements", id).with(staffUser()))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/v1/cash-sessions/{id}/movements", id)
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody("income", 100L)))
				.andExpect(status().isNotFound());
	}

	@Test
	void invalidUuidReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/cash-sessions/{id}/movements", "not-a-uuid").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
		mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", "not-a-uuid")
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(closeBody(0L)))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/v1/cash-sessions/{id}/movements", "not-a-uuid")
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody("income", 100L)))
				.andExpect(status().isBadRequest());
	}

	// ---------- Security

	static Stream<Arguments> cashEndpoints() {
		String id = UUID.randomUUID().toString();
		return Stream.of(
				Arguments.of(HttpMethod.GET, "/api/v1/cash-sessions/current"),
				Arguments.of(HttpMethod.POST, "/api/v1/cash-sessions/open"),
				Arguments.of(HttpMethod.POST, "/api/v1/cash-sessions/" + id + "/close"),
				Arguments.of(HttpMethod.GET, "/api/v1/cash-sessions/" + id + "/movements"),
				Arguments.of(HttpMethod.POST, "/api/v1/cash-sessions/" + id + "/movements")
		);
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("cashEndpoints")
	void endpointWithoutTokenReturnsUnauthorized(HttpMethod method, String path) throws Exception {
		mockMvc.perform(request(method, path)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("cashEndpoints")
	void endpointWithInvalidTokenReturnsUnauthorized(HttpMethod method, String path) throws Exception {
		mockMvc.perform(request(method, path)
						.header("Authorization", "Bearer invalid.jwt.token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized());
	}

	// ---------- Helpers

	private RequestPostProcessor cashierUser() {
		return user(cashier.getEmail());
	}

	private long countOpenSessions() {
		return cashSessionRepository.findAll().stream()
				.filter(session -> session.getStatus() == CashSessionStatus.open)
				.count();
	}

	private UUID trackSession(MvcResult result) throws Exception {
		UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
		sessionIds.add(id);
		return id;
	}

	private String openSession(long openingBalanceCents) throws Exception {
		return openSession(openingBalanceCents, null);
	}

	private String openSession(long openingBalanceCents, String notes) throws Exception {
		String body = notes == null
				? openBody(openingBalanceCents)
				: """
						{"openingBalanceCents": %d, "notes": "%s"}
						""".formatted(openingBalanceCents, notes);
		MvcResult result = mockMvc.perform(post("/api/v1/cash-sessions/open")
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn();
		return trackSession(result).toString();
	}

	private void closeSession(String sessionId, long countedBalanceCents) throws Exception {
		mockMvc.perform(post("/api/v1/cash-sessions/{id}/close", sessionId)
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(closeBody(countedBalanceCents)))
				.andExpect(status().isOk());
	}

	private String createMovement(String sessionId, String type, long amountCents) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/cash-sessions/{id}/movements", sessionId)
						.with(cashierUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody(type, amountCents)))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private User createStaffUser(String firstName) {
		Role role = new Role();
		role.setCode("cash_" + uniqueSuffix());
		role.setName("Cash tester");
		role.setCreatedAt(now());
		role.setUpdatedAt(now());
		role = roleRepository.save(role);
		roleIds.add(role.getId());

		User staff = new User();
		staff.setFirstName(firstName);
		staff.setLastName("Tester");
		staff.setEmail("cash.%s@aurora.test".formatted(uniqueSuffix()));
		staff.setPasswordHash("not-used");
		staff.setRole(role);
		staff.setStatus(UserStatus.active);
		staff.setCreatedAt(now());
		staff.setUpdatedAt(now());
		staff = userRepository.save(staff);
		userIds.add(staff.getId());
		return staff;
	}

	private static String openBody(long openingBalanceCents) {
		return """
				{"openingBalanceCents": %d}
				""".formatted(openingBalanceCents);
	}

	private static String closeBody(long countedBalanceCents) {
		return """
				{"countedBalanceCents": %d}
				""".formatted(countedBalanceCents);
	}

	private static String movementBody(String type, long amountCents) {
		return """
				{"type": "%s", "concept": "Movimiento de prueba", "amountCents": %d}
				""".formatted(type, amountCents);
	}
}
