package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.aurora.pms.dto.request.CreateInventoryMovementRequest;
import com.aurora.pms.dto.response.InventoryMovementResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.model.InventoryItem;
import com.aurora.pms.model.Product;
import com.aurora.pms.model.Role;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.InventoryMovementReason;
import com.aurora.pms.model.enums.InventoryMovementType;
import com.aurora.pms.model.enums.ProductCategory;
import com.aurora.pms.model.enums.UserStatus;
import com.aurora.pms.repository.InventoryItemRepository;
import com.aurora.pms.repository.InventoryMovementRepository;
import com.aurora.pms.repository.ProductRepository;
import com.aurora.pms.repository.RoleRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.security.SecurityPermissions;
import com.aurora.pms.service.InventoryService;
import com.jayway.jsonpath.JsonPath;

class InventoryControllerTest extends AbstractCatalogApiTest {

	private static final String BASE_PATH = "/api/v1/inventory/items";

	@Autowired
	private InventoryItemRepository inventoryItemRepository;

	@Autowired
	private InventoryMovementRepository inventoryMovementRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private InventoryService inventoryService;

	private final List<UUID> itemIds = new ArrayList<>();
	private final List<UUID> productIds = new ArrayList<>();
	private final List<UUID> userIds = new ArrayList<>();
	private final List<UUID> roleIds = new ArrayList<>();

	/** Categoría única por test: aísla los filtros de los datos que ya haya en la BD. */
	private final String category = "test-" + uniqueSuffix();

	@AfterEach
	void cleanUpInventoryData() {
		itemIds.forEach(itemId -> inventoryMovementRepository.deleteAll(
				inventoryMovementRepository.findByInventoryItemIdOrderByOccurredAtAscCreatedAtAsc(itemId)));
		inventoryItemRepository.deleteAllById(itemIds);
		productRepository.deleteAllById(productIds);
		userRepository.deleteAllById(userIds);
		roleRepository.deleteAllById(roleIds);
	}

	// ---------- Items

	@Test
	void findItemReturnsItemWithComputedLowStock() throws Exception {
		InventoryItem item = createItem("Toallas", 3, 5, true);

		mockMvc.perform(get(BASE_PATH + "/{itemId}", item.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(item.getId().toString()))
				.andExpect(jsonPath("$.sku").value(item.getSku()))
				.andExpect(jsonPath("$.name").value("Toallas"))
				.andExpect(jsonPath("$.category").value(category))
				.andExpect(jsonPath("$.unit").value("unit"))
				.andExpect(jsonPath("$.currentQuantity").value(3))
				.andExpect(jsonPath("$.minimumQuantity").value(5))
				.andExpect(jsonPath("$.lowStock").value(true))
				.andExpect(jsonPath("$.productId").value(nullValue()))
				.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void listFiltersByCategoryActiveAndLowStock() throws Exception {
		InventoryItem low = createItem("A low", 2, 5, true);
		InventoryItem atMinimum = createItem("B at minimum", 5, 5, true);
		InventoryItem enough = createItem("C enough", 10, 5, true);
		InventoryItem inactive = createItem("D inactive", 0, 5, false);
		createItem("Other category", 0, 5, true, "other-" + uniqueSuffix());

		mockMvc.perform(get(BASE_PATH).param("category", category).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[0].id").value(low.getId().toString()))
				.andExpect(jsonPath("$[3].id").value(inactive.getId().toString()));

		// La categoría no distingue mayúsculas ni espacios.
		mockMvc.perform(get(BASE_PATH).param("category", " " + category.toUpperCase() + " ").with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(4)));

		mockMvc.perform(get(BASE_PATH).param("category", category).param("active", "true").with(staffUser()))
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[*].id", not(hasItem(inactive.getId().toString()))));

		mockMvc.perform(get(BASE_PATH).param("category", category).param("active", "false").with(staffUser()))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(inactive.getId().toString()));

		mockMvc.perform(get(BASE_PATH).param("category", category).param("lowStock", "true").with(staffUser()))
				.andExpect(jsonPath("$[*].id", containsInAnyOrder(
						low.getId().toString(), atMinimum.getId().toString(), inactive.getId().toString())))
				.andExpect(jsonPath("$[*].lowStock", everyItem(is(true))));

		mockMvc.perform(get(BASE_PATH).param("category", category).param("lowStock", "false").with(staffUser()))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(enough.getId().toString()))
				.andExpect(jsonPath("$[0].lowStock").value(false));

		mockMvc.perform(get(BASE_PATH)
						.param("category", category)
						.param("active", "true")
						.param("lowStock", "true")
						.with(staffUser()))
				.andExpect(jsonPath("$[*].id", containsInAnyOrder(
						low.getId().toString(), atMinimum.getId().toString())));
	}

	@Test
	void listWithoutFiltersIncludesItems() throws Exception {
		InventoryItem item = createItem("Sin filtros", 1, 0, true);

		mockMvc.perform(get(BASE_PATH).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(item.getId().toString())));
	}

	@Test
	void invalidFiltersReturnBadRequest() throws Exception {
		mockMvc.perform(get(BASE_PATH).param("lowStock", "maybe").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid value for parameter 'lowStock'"));
		mockMvc.perform(get(BASE_PATH).param("active", "yes-no").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid value for parameter 'active'"));
	}

	@Test
	void missingItemReturnsNotFound() throws Exception {
		UUID id = UUID.randomUUID();

		mockMvc.perform(get(BASE_PATH + "/{itemId}", id).with(staffUser()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Inventory item not found")));
		mockMvc.perform(get(BASE_PATH + "/{itemId}/movements", id).with(staffUser()))
				.andExpect(status().isNotFound());
		mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", id)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody("in", "purchase", 1)))
				.andExpect(status().isNotFound());
	}

	@Test
	void invalidUuidReturnsBadRequest() throws Exception {
		mockMvc.perform(get(BASE_PATH + "/{itemId}", "not-a-uuid").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid value for parameter 'itemId'"));
		mockMvc.perform(get(BASE_PATH + "/{itemId}/movements", "not-a-uuid").with(staffUser()))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", "not-a-uuid")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody("in", "purchase", 1)))
				.andExpect(status().isBadRequest());
	}

	// ---------- Movements

	@Test
	void entryAddsToCurrentQuantityAndIgnoresServerFields() throws Exception {
		InventoryItem item = createItem("Jabón", 4, 2, true);

		mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", item.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"type": "in", "reason": "purchase", "quantity": 6, "notes": " Proveedor A ",
								 "id": "%s", "inventoryItemId": "%s", "responsibleUserId": "%s",
								 "occurredAt": "2000-01-01T00:00:00Z", "createdAt": "2000-01-01T00:00:00Z"}
								""".formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.inventoryItemId").value(item.getId().toString()))
				.andExpect(jsonPath("$.type").value("in"))
				.andExpect(jsonPath("$.reason").value("purchase"))
				.andExpect(jsonPath("$.quantity").value(6))
				.andExpect(jsonPath("$.notes").value("Proveedor A"))
				.andExpect(jsonPath("$.responsibleUserId").value(nullValue()))
				.andExpect(jsonPath("$.occurredAt").value(not("2000-01-01T00:00:00Z")))
				.andExpect(jsonPath("$.createdAt").value(not("2000-01-01T00:00:00Z")));

		assertThat(currentQuantity(item)).isEqualTo(10);
	}

	@Test
	void exitSubtractsFromCurrentQuantity() throws Exception {
		InventoryItem item = createItem("Shampoo", 10, 2, true);

		mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", item.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody("out", "consumption", 4)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("out"))
				.andExpect(jsonPath("$.quantity").value(4));

		assertThat(currentQuantity(item)).isEqualTo(6);
	}

	@Test
	void exitCanLeaveStockAtExactlyZero() throws Exception {
		InventoryItem item = createItem("Agua", 3, 0, true);

		createMovement(item, "out", "sale", 3);

		assertThat(currentQuantity(item)).isZero();
		mockMvc.perform(get(BASE_PATH + "/{itemId}", item.getId()).with(staffUser()))
				.andExpect(jsonPath("$.currentQuantity").value(0))
				.andExpect(jsonPath("$.lowStock").value(true));
	}

	@Test
	void insufficientStockReturnsBadRequestAndKeepsQuantity() throws Exception {
		InventoryItem item = createItem("Café", 2, 0, true);

		mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", item.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody("out", "shrinkage", 3)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Insufficient stock: available 2, requested 3"));

		assertThat(currentQuantity(item)).isEqualTo(2);
		assertThat(movementsOf(item)).isEmpty();
	}

	@Test
	void currentQuantityIsCalculatedFromSequenceOfMovements() throws Exception {
		InventoryItem item = createItem("Sábanas", 0, 5, true);

		String first = createMovement(item, "in", "purchase", 20);
		String second = createMovement(item, "out", "consumption", 7);
		String third = createMovement(item, "in", "restock", 3);
		String fourth = createMovement(item, "out", "shrinkage", 1);

		assertThat(currentQuantity(item)).isEqualTo(15);
		mockMvc.perform(get(BASE_PATH + "/{itemId}/movements", item.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[0].id").value(first))
				.andExpect(jsonPath("$[1].id").value(second))
				.andExpect(jsonPath("$[2].id").value(third))
				.andExpect(jsonPath("$[3].id").value(fourth));
	}

	@Test
	void listMovementsReturnsOnlyMovementsOfItem() throws Exception {
		InventoryItem item = createItem("Item A", 5, 0, true);
		InventoryItem other = createItem("Item B", 5, 0, true);
		String own = createMovement(item, "in", "purchase", 1);
		createMovement(other, "in", "purchase", 1);

		mockMvc.perform(get(BASE_PATH + "/{itemId}/movements", item.getId()).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(own));
	}

	@ParameterizedTest(name = "{0} + {1}")
	@CsvSource({
			"in, consumption",
			"in, sale",
			"in, shrinkage",
			"out, purchase",
			"out, restock",
			"in, room_service_return",
			"out, room_service_return"
	})
	void invalidTypeReasonCombinationReturnsBadRequest(String type, String reason) throws Exception {
		InventoryItem item = createItem("Combinación", 10, 0, true);

		mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", item.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody(type, reason, 1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("Reason " + reason + " is not valid for movement type " + type));

		assertThat(currentQuantity(item)).isEqualTo(10);
		assertThat(movementsOf(item)).isEmpty();
	}

	@ParameterizedTest(name = "{0} + {1}")
	@CsvSource({
			"in, purchase",
			"in, restock",
			"out, consumption",
			"out, sale",
			"out, shrinkage"
	})
	void validTypeReasonCombinationsAreAccepted(String type, String reason) throws Exception {
		InventoryItem item = createItem("Combinación", 10, 0, true);

		createMovement(item, type, reason, 1);

		assertThat(currentQuantity(item)).isEqualTo("in".equals(type) ? 11 : 9);
	}

	@Test
	void inactiveItemRejectsMovements() throws Exception {
		InventoryItem item = createItem("Descontinuado", 5, 0, false);

		mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", item.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody("in", "purchase", 1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Inventory item is inactive"));

		// Un item inactivo sigue siendo consultable.
		mockMvc.perform(get(BASE_PATH + "/{itemId}", item.getId()).with(staffUser()))
				.andExpect(status().isOk());
		assertThat(currentQuantity(item)).isEqualTo(5);
	}

	@Test
	void invalidQuantitiesReturnBadRequest() throws Exception {
		InventoryItem item = createItem("Cantidades", 10, 0, true);

		for (String quantity : List.of("0", "-5", "null")) {
			mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", item.getId())
							.with(staffUser())
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"type": "in", "reason": "purchase", "quantity": %s}
									""".formatted(quantity)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.quantity").exists());
		}
		for (String quantity : List.of("1.5", "\"3\"", "3000000000")) {
			mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", item.getId())
							.with(staffUser())
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"type": "in", "reason": "purchase", "quantity": %s}
									""".formatted(quantity)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value("Malformed or invalid request body"));
		}

		assertThat(currentQuantity(item)).isEqualTo(10);
		assertThat(movementsOf(item)).isEmpty();
	}

	@Test
	void entryThatOverflowsQuantityReturnsBadRequest() throws Exception {
		InventoryItem item = createItem("Desbordamiento", Integer.MAX_VALUE - 1, 0, true);

		mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", item.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody("in", "purchase", 2)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Inventory quantity is too large"));
	}

	@Test
	void missingFieldsAndInvalidEnumsReturnBadRequest() throws Exception {
		InventoryItem item = createItem("Enums", 10, 0, true);

		mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", item.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"quantity": 1}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.type").exists())
				.andExpect(jsonPath("$.errors.reason").exists());
		mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", item.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody("transfer", "purchase", 1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Malformed or invalid request body"));
		mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", item.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody("in", "gift", 1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Malformed or invalid request body"));
	}

	@Test
	void movementRecordsAuthenticatedUser() throws Exception {
		InventoryItem item = createItem("Responsable", 10, 0, true);
		User staff = createStaffUser();

		mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", item.getId())
						.with(userWithPermissions(staff.getEmail(), SecurityPermissions.INVENTORY_WRITE))
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody("out", "consumption", 1)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.responsibleUserId").value(staff.getId().toString()));
	}

	@Test
	void movementDoesNotModifyLinkedProductStock() throws Exception {
		Product product = createProduct(7);
		InventoryItem item = createItem("Con producto", 10, 0, true);
		item.setProduct(product);
		inventoryItemRepository.save(item);

		createMovement(item, "out", "sale", 4);
		createMovement(item, "in", "restock", 10);

		mockMvc.perform(get(BASE_PATH + "/{itemId}", item.getId()).with(staffUser()))
				.andExpect(jsonPath("$.productId").value(product.getId().toString()))
				.andExpect(jsonPath("$.currentQuantity").value(16));
		assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(7);
	}

	// ---------- Concurrency

	@Test
	void concurrentExitsNeverOversellStock() throws Exception {
		InventoryItem item = createItem("Concurrente", 5, 0, true);

		// Cada salida por sí sola es válida, pero juntas superan el stock: solo una puede entrar.
		List<Outcome> outcomes = runConcurrentExits(item, 2, 3);

		assertThat(outcomes.stream().filter(Outcome::succeeded).count()).isEqualTo(1);
		assertThat(outcomes.stream().filter(outcome -> !outcome.succeeded()).count()).isEqualTo(1);
		assertThat(currentQuantity(item)).isEqualTo(2);
		assertThat(movementsOf(item)).hasSize(1);
	}

	@Test
	void concurrentExitsDoNotLoseUpdates() throws Exception {
		InventoryItem item = createItem("Concurrente", 20, 0, true);

		List<Outcome> outcomes = runConcurrentExits(item, 8, 2);

		assertThat(outcomes).allMatch(Outcome::succeeded);
		assertThat(currentQuantity(item)).isEqualTo(4);
		assertThat(movementsOf(item)).hasSize(8);
	}

	private List<Outcome> runConcurrentExits(InventoryItem item, int threads, int quantity) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		Callable<InventoryMovementResponse> exit = () -> {
			start.await(2, TimeUnit.SECONDS);
			return inventoryService.createMovement(
					item.getId(),
					new CreateInventoryMovementRequest(
							InventoryMovementType.out, InventoryMovementReason.consumption, quantity, null),
					null);
		};

		try {
			List<Future<InventoryMovementResponse>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(executor.submit(exit));
			}
			start.countDown();

			List<Outcome> outcomes = new ArrayList<>();
			for (Future<InventoryMovementResponse> future : futures) {
				try {
					future.get(10, TimeUnit.SECONDS);
					outcomes.add(new Outcome(true));
				} catch (ExecutionException exception) {
					assertThat(exception.getCause()).isInstanceOf(BadRequestException.class);
					outcomes.add(new Outcome(false));
				}
			}
			return outcomes;
		} finally {
			executor.shutdownNow();
		}
	}

	private record Outcome(boolean succeeded) {
	}

	// ---------- Security

	static Stream<Arguments> inventoryEndpoints() {
		String id = UUID.randomUUID().toString();
		return Stream.of(
				Arguments.of(HttpMethod.GET, BASE_PATH),
				Arguments.of(HttpMethod.GET, BASE_PATH + "/" + id),
				Arguments.of(HttpMethod.GET, BASE_PATH + "/" + id + "/movements"),
				Arguments.of(HttpMethod.POST, BASE_PATH + "/" + id + "/movements")
		);
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("inventoryEndpoints")
	void endpointWithoutTokenReturnsUnauthorized(HttpMethod method, String path) throws Exception {
		mockMvc.perform(request(method, path)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@ParameterizedTest(name = "{0} {1}")
	@MethodSource("inventoryEndpoints")
	void endpointWithInvalidTokenReturnsUnauthorized(HttpMethod method, String path) throws Exception {
		mockMvc.perform(request(method, path)
						.header("Authorization", "Bearer invalid.jwt.token")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnauthorized());
	}

	// ---------- Helpers

	private InventoryItem createItem(String name, int currentQuantity, int minimumQuantity, boolean active) {
		return createItem(name, currentQuantity, minimumQuantity, active, category);
	}

	private InventoryItem createItem(
			String name,
			int currentQuantity,
			int minimumQuantity,
			boolean active,
			String itemCategory
	) {
		InventoryItem item = new InventoryItem();
		item.setSku("INV-" + uniqueSuffix());
		item.setName(name);
		item.setDescription("Test item");
		item.setCategory(itemCategory);
		item.setUnit("unit");
		item.setCurrentQuantity(currentQuantity);
		item.setMinimumQuantity(minimumQuantity);
		item.setActive(active);
		item.setCreatedAt(now());
		item.setUpdatedAt(now());
		item = inventoryItemRepository.save(item);
		itemIds.add(item.getId());
		return item;
	}

	private Product createProduct(int stockQuantity) {
		Product product = new Product();
		product.setSku("PRD-" + uniqueSuffix());
		product.setName("Test product");
		product.setCategory(ProductCategory.minibar);
		product.setPriceCents(1500L);
		product.setStockQuantity(stockQuantity);
		product.setCreatedAt(now());
		product.setUpdatedAt(now());
		product = productRepository.save(product);
		productIds.add(product.getId());
		return product;
	}

	private User createStaffUser() {
		Role role = new Role();
		role.setCode("inventory_" + uniqueSuffix());
		role.setName("Inventory tester");
		role.setCreatedAt(now());
		role.setUpdatedAt(now());
		role = roleRepository.save(role);
		roleIds.add(role.getId());

		User staff = new User();
		staff.setFirstName("Inventory");
		staff.setLastName("Tester");
		staff.setEmail("inventory.%s@aurora.test".formatted(uniqueSuffix()));
		staff.setPasswordHash("not-used");
		staff.setRole(role);
		staff.setStatus(UserStatus.active);
		staff.setCreatedAt(now());
		staff.setUpdatedAt(now());
		staff = userRepository.save(staff);
		userIds.add(staff.getId());
		return staff;
	}

	private String createMovement(InventoryItem item, String type, String reason, int quantity) throws Exception {
		MvcResult result = mockMvc.perform(post(BASE_PATH + "/{itemId}/movements", item.getId())
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content(movementBody(type, reason, quantity)))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private int currentQuantity(InventoryItem item) {
		return inventoryItemRepository.findById(item.getId()).orElseThrow().getCurrentQuantity();
	}

	private List<?> movementsOf(InventoryItem item) {
		return inventoryMovementRepository.findByInventoryItemIdOrderByOccurredAtAscCreatedAtAsc(item.getId());
	}

	private static String movementBody(String type, String reason, int quantity) {
		return """
				{"type": "%s", "reason": "%s", "quantity": %d}
				""".formatted(type, reason, quantity);
	}
}
