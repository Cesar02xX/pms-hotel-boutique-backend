package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Charge;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.GuestAccount;
import com.aurora.pms.model.InventoryItem;
import com.aurora.pms.model.InventoryMovement;
import com.aurora.pms.model.Order;
import com.aurora.pms.model.OrderItem;
import com.aurora.pms.model.Product;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.ChargeCategory;
import com.aurora.pms.model.enums.ChargeStatus;
import com.aurora.pms.model.enums.GuestAccountStatus;
import com.aurora.pms.model.enums.InventoryMovementReason;
import com.aurora.pms.model.enums.InventoryMovementType;
import com.aurora.pms.model.enums.OrderStatus;
import com.aurora.pms.model.enums.ProductCategory;
import com.aurora.pms.repository.ChargeRepository;
import com.aurora.pms.repository.GuestAccountRepository;
import com.aurora.pms.repository.InventoryItemRepository;
import com.aurora.pms.repository.InventoryMovementRepository;
import com.aurora.pms.repository.OrderItemRepository;
import com.aurora.pms.repository.OrderRepository;
import com.aurora.pms.repository.ProductRepository;
import com.aurora.pms.service.RoomServiceOrderService;
import com.jayway.jsonpath.JsonPath;

class RoomServiceControllerTest extends AbstractCatalogApiTest {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderItemRepository orderItemRepository;

	@Autowired
	private InventoryItemRepository inventoryItemRepository;

	@Autowired
	private InventoryMovementRepository inventoryMovementRepository;

	@Autowired
	private RoomServiceOrderService roomServiceOrderService;

	@Autowired
	private ChargeRepository chargeRepository;

	@Autowired
	private GuestAccountRepository guestAccountRepository;

	private final List<UUID> orderIds = new ArrayList<>();
	private final List<UUID> productIds = new ArrayList<>();
	private final List<UUID> inventoryItemIds = new ArrayList<>();
	private final List<UUID> folioBookingIds = new ArrayList<>();

	@AfterEach
	void cleanUpRoomServiceData() {
		inventoryItemIds.forEach(itemId -> inventoryMovementRepository.deleteAll(
				inventoryMovementRepository.findByInventoryItemIdOrderByOccurredAtAscCreatedAtAsc(itemId)));
		inventoryItemRepository.deleteAllById(inventoryItemIds);
		orderIds.forEach(orderId -> orderItemRepository.deleteAll(orderItemRepository.findByOrderIdOrderById(orderId)));
		orderRepository.deleteAllById(orderIds);
		folioBookingIds.forEach(bookingId -> {
			chargeRepository.deleteAll(chargeRepository.findByBookingIdOrderByChargedAtAscCreatedAtAsc(bookingId));
			guestAccountRepository.findByBookingId(bookingId).ifPresent(guestAccountRepository::delete);
		});
		productRepository.deleteAllById(productIds);
	}

	@Test
	void listProductsReturnsOnlyActiveProducts() throws Exception {
		Product activeProduct = createProduct(ProductCategory.food_and_beverage, true, 2500L);
		Product inactiveProduct = createProduct(ProductCategory.food_and_beverage, false, 1500L);

		mockMvc.perform(get("/api/v1/room-service/products").with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(activeProduct.getId().toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(inactiveProduct.getId().toString()))));
	}

	@Test
	void listProductsFiltersByCategory() throws Exception {
		Product minibarProduct = createProduct(ProductCategory.minibar, true, 900L);
		Product shopProduct = createProduct(ProductCategory.shop, true, 1200L);

		mockMvc.perform(get("/api/v1/room-service/products")
						.param("category", "minibar")
						.with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(minibarProduct.getId().toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(shopProduct.getId().toString()))));
	}

	@Test
	void createOrderReturnsCreatedWithItemsAndCalculatedTotal() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product burger = createProduct(ProductCategory.food_and_beverage, true, 3500L);
		Product soda = createProduct(ProductCategory.minibar, true, 800L);

		MvcResult result = mockMvc.perform(post("/api/v1/room-service/orders")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookingId": "%s", "notes": "Sin cebolla",
								 "items": [
								   {"productId": "%s", "quantity": 2, "unitPriceCents": 1},
								   {"productId": "%s", "quantity": 3}
								 ],
								 "status": "delivered", "currency": "USD", "chargeId": "%s",
								 "requestedAt": "2000-01-01T00:00:00Z", "createdAt": "2000-01-01T00:00:00Z"}
								""".formatted(booking.getId(), burger.getId(), soda.getId(), UUID.randomUUID())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.bookingId").value(booking.getId().toString()))
				.andExpect(jsonPath("$.roomId").value(booking.getRoom().getId().toString()))
				.andExpect(jsonPath("$.guestId").value(booking.getGuest().getId().toString()))
				.andExpect(jsonPath("$.status").value("pending"))
				.andExpect(jsonPath("$.currency").value("GTQ"))
				.andExpect(jsonPath("$.notes").value("Sin cebolla"))
				.andExpect(jsonPath("$.totalCents").value(9400))
				.andExpect(jsonPath("$.items[?(@.productId == '%s')].quantity".formatted(burger.getId()))
						.value(hasItem(2)))
				.andExpect(jsonPath("$.items[?(@.productId == '%s')].unitPriceCents".formatted(burger.getId()))
						.value(hasItem(3500)))
				.andExpect(jsonPath("$.items[?(@.productId == '%s')].lineTotalCents".formatted(soda.getId()))
						.value(hasItem(2400)))
				.andExpect(jsonPath("$.requestedAt").value(not("2000-01-01T00:00:00Z")))
				.andReturn();

		UUID orderId = trackCreatedOrder(result);
		Order saved = orderRepository.findById(orderId).orElseThrow();
		assertThat(saved.getCharge()).isNull();
		assertThat(saved.getStatus()).isEqualTo(OrderStatus.pending);
		assertThat(productRepository.findById(burger.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
		assertThat(orderItemRepository.findByOrderIdOrderById(orderId)).hasSize(2);
	}

	@Test
	void getOrderReturnsItemsWithSnapshotPrice() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createProduct(ProductCategory.food_and_beverage, true, 2200L);
		String orderId = createOrder(booking, product, 2);
		product.setPriceCents(9999L);
		productRepository.save(product);

		mockMvc.perform(get("/api/v1/room-service/orders/{orderId}", orderId).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(orderId))
				.andExpect(jsonPath("$.totalCents").value(4400))
				.andExpect(jsonPath("$.items[0].unitPriceCents").value(2200));
	}

	@Test
	void listOrdersFiltersByBookingAndStatus() throws Exception {
		Booking firstBooking = createRoomServiceBooking();
		Booking secondBooking = createRoomServiceBooking();
		Product product = createProduct(ProductCategory.food_and_beverage, true, 1000L);
		String matchingOrderId = createOrder(firstBooking, product, 1);
		Order matchingOrder = orderRepository.findById(UUID.fromString(matchingOrderId)).orElseThrow();
		matchingOrder.setStatus(OrderStatus.accepted);
		orderRepository.save(matchingOrder);
		String wrongStatusId = createOrder(firstBooking, product, 1);
		String wrongBookingId = createOrder(secondBooking, product, 1);
		Order wrongBooking = orderRepository.findById(UUID.fromString(wrongBookingId)).orElseThrow();
		wrongBooking.setStatus(OrderStatus.accepted);
		orderRepository.save(wrongBooking);

		mockMvc.perform(get("/api/v1/room-service/orders")
						.param("bookingId", firstBooking.getId().toString())
						.param("status", "accepted")
						.with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(matchingOrderId)))
				.andExpect(jsonPath("$[*].id", not(hasItem(wrongStatusId))))
				.andExpect(jsonPath("$[*].id", not(hasItem(wrongBookingId))));
	}

	@Test
	void emptyItemsReturnBadRequest() throws Exception {
		Booking booking = createRoomServiceBooking();

		mockMvc.perform(post("/api/v1/room-service/orders")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookingId": "%s", "items": []}
								""".formatted(booking.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.items").exists());
	}

	@Test
	void inactiveOrMissingProductReturnsNotFound() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product inactiveProduct = createProduct(ProductCategory.minibar, false, 800L);

		createOrderWithProductExpectingNotFound(booking, inactiveProduct.getId());
		createOrderWithProductExpectingNotFound(booking, UUID.randomUUID());
	}

	@Test
	void invalidQuantityReturnsBadRequest() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createProduct(ProductCategory.minibar, true, 800L);

		mockMvc.perform(post("/api/v1/room-service/orders")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookingId": "%s", "items": [{"productId": "%s", "quantity": 0}]}
								""".formatted(booking.getId(), product.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors['items[0].quantity']").exists());
	}

	@Test
	void missingBookingReturnsNotFound() throws Exception {
		Product product = createProduct(ProductCategory.minibar, true, 800L);

		mockMvc.perform(post("/api/v1/room-service/orders")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookingId": "%s", "items": [{"productId": "%s", "quantity": 1}]}
								""".formatted(UUID.randomUUID(), product.getId())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Booking not found")));
	}

	@Test
	void validStatusFlowReturnsOk() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createStockedProduct(1500L, 100);
		openFolio(booking);
		String orderId = createOrder(booking, product, 1);

		updateStatusExpectingOk(orderId, "accepted");
		updateStatusExpectingOk(orderId, "preparing");
		updateStatusExpectingOk(orderId, "ready");
		updateStatusExpectingOk(orderId, "on_the_way");
		updateStatusExpectingOk(orderId, "delivered");
	}

	@Test
	void pendingCanBeRejected() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createProduct(ProductCategory.food_and_beverage, true, 1500L);
		String orderId = createOrder(booking, product, 1);

		updateStatusExpectingOk(orderId, "rejected");
	}

	@Test
	void ordersCanBeCancelledFromPendingAcceptedPreparingAndReady() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createStockedProduct(1500L, 100);
		List<String> flow = List.of("accepted", "preparing", "ready");

		for (int steps = 0; steps <= flow.size(); steps++) {
			String orderId = createOrder(booking, product, 1);
			for (String status : flow.subList(0, steps)) {
				updateStatusExpectingOk(orderId, status);
			}
			updateStatusExpectingOk(orderId, "cancelled");
		}
	}

	@Test
	void onTheWayOrderCannotBeCancelled() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createStockedProduct(1500L, 100);
		String orderId = createOrder(booking, product, 1);
		updateStatusExpectingOk(orderId, "accepted");
		updateStatusExpectingOk(orderId, "preparing");
		updateStatusExpectingOk(orderId, "ready");
		updateStatusExpectingOk(orderId, "on_the_way");

		updateStatusExpectingBadRequest(orderId, "cancelled", "Invalid room service order status transition");
		assertThat(orderRepository.findById(UUID.fromString(orderId)).orElseThrow().getStatus())
				.isEqualTo(OrderStatus.on_the_way);
	}

	@Test
	void terminalStatusesCannotBeCancelled() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createProduct(ProductCategory.food_and_beverage, true, 1500L);

		for (OrderStatus terminal : List.of(OrderStatus.delivered, OrderStatus.rejected, OrderStatus.cancelled)) {
			String orderId = createOrder(booking, product, 1);
			Order order = orderRepository.findById(UUID.fromString(orderId)).orElseThrow();
			order.setStatus(terminal);
			orderRepository.save(order);

			updateStatusExpectingBadRequest(orderId, "cancelled",
					terminal == OrderStatus.cancelled
							? "Room service order is already"
							: "Room service order status is terminal");
		}
	}

	@Test
	void createOrderRequiresCheckedInBooking() throws Exception {
		Product product = createProduct(ProductCategory.minibar, true, 800L);

		for (BookingStatus status : List.of(BookingStatus.pending, BookingStatus.confirmed,
				BookingStatus.checked_out, BookingStatus.cancelled, BookingStatus.no_show)) {
			Booking booking = createRoomServiceBooking(status);

			mockMvc.perform(post("/api/v1/room-service/orders")
							.with(staffUser())
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"bookingId": "%s", "items": [{"productId": "%s", "quantity": 1}]}
									""".formatted(booking.getId(), product.getId())))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.message").value(startsWith("Room service orders require a checked_in booking")));

			assertThat(orderRepository.findWithFilters(booking.getId(), null)).isEmpty();
		}
	}

	@Test
	void decimalNegativeOrTextQuantityReturnsBadRequestWithoutCreatingOrder() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createProduct(ProductCategory.minibar, true, 800L);

		for (String quantity : List.of("1.5", "2.0", "-1", "\"2\"", "2147483648")) {
			mockMvc.perform(post("/api/v1/room-service/orders")
							.with(staffUser())
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"bookingId": "%s", "items": [{"productId": "%s", "quantity": %s}]}
									""".formatted(booking.getId(), product.getId(), quantity)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.status").value(400));
		}

		assertThat(orderRepository.findWithFilters(booking.getId(), null)).isEmpty();
	}

	@Test
	void repeatedProductsAreConsolidatedIntoOneLine() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product burger = createProduct(ProductCategory.food_and_beverage, true, 3500L);
		Product soda = createProduct(ProductCategory.minibar, true, 800L);

		MvcResult result = mockMvc.perform(post("/api/v1/room-service/orders")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookingId": "%s",
								 "items": [
								   {"productId": "%s", "quantity": 1},
								   {"productId": "%s", "quantity": 2},
								   {"productId": "%s", "quantity": 3}
								 ]}
								""".formatted(booking.getId(), burger.getId(), soda.getId(), burger.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[?(@.productId == '%s')].quantity".formatted(burger.getId()))
						.value(hasItem(4)))
				.andExpect(jsonPath("$.items[?(@.productId == '%s')].lineTotalCents".formatted(burger.getId()))
						.value(hasItem(14000)))
				.andExpect(jsonPath("$.totalCents").value(15600))
				.andReturn();

		UUID orderId = trackCreatedOrder(result);
		assertThat(orderItemRepository.findByOrderIdOrderById(orderId)).hasSize(2);
	}

	// ---------- Inventario

	@Test
	void pendingOrderDoesNotDeductInventory() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createStockedProduct(1500L, 10);

		String orderId = createOrder(booking, product, 3);

		assertThat(stockOf(product)).isEqualTo(10);
		assertThat(movementsOfOrder(orderId)).isEmpty();
		assertThat(orderRepository.findById(UUID.fromString(orderId)).orElseThrow().getInventoryDeductedAt()).isNull();
	}

	@Test
	void acceptDeductsEveryLineWithTraceableMovements() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product burger = createStockedProduct(3500L, 10);
		Product soda = createStockedProduct(800L, 5);
		String orderId = createOrder(booking, List.of(burger, soda), List.of(2, 5));

		updateStatusExpectingOk(orderId, "accepted");

		assertThat(stockOf(burger)).isEqualTo(8);
		assertThat(stockOf(soda)).isZero();
		List<InventoryMovement> movements = movementsOfOrder(orderId);
		assertThat(movements).hasSize(2)
				.allSatisfy(movement -> {
					assertThat(movement.getType()).isEqualTo(InventoryMovementType.out);
					assertThat(movement.getReason()).isEqualTo(InventoryMovementReason.sale);
				});
		assertThat(movements).extracting(InventoryMovement::getQuantity).containsExactlyInAnyOrder(2, 5);
		assertThat(orderRepository.findById(UUID.fromString(orderId)).orElseThrow().getInventoryDeductedAt())
				.isNotNull();

		mockMvc.perform(get("/api/v1/inventory/items/{itemId}/movements", inventoryItemOf(burger).getId())
						.with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].reason").value("sale"))
				.andExpect(jsonPath("$[0].roomServiceOrderId").value(orderId));
	}

	@Test
	void insufficientStockOnAnyLineRejectsAcceptanceWithoutPartialDeduction() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product available = createStockedProduct(3500L, 10);
		Product scarce = createStockedProduct(800L, 1);
		String orderId = createOrder(booking, List.of(available, scarce), List.of(2, 2));

		updateStatusExpectingBadRequest(orderId, "accepted", "Insufficient stock for product");

		assertThat(stockOf(available)).isEqualTo(10);
		assertThat(stockOf(scarce)).isEqualTo(1);
		assertThat(movementsOfOrder(orderId)).isEmpty();
		Order order = orderRepository.findById(UUID.fromString(orderId)).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.pending);
		assertThat(order.getInventoryDeductedAt()).isNull();
	}

	@Test
	void acceptRequiresExactlyOneActiveInventoryItemPerProduct() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product withoutItem = createProduct(ProductCategory.minibar, true, 800L);
		Product withInactiveItem = createProduct(ProductCategory.minibar, true, 800L);
		createInventoryItem(withInactiveItem, 10, false);
		Product withTwoItems = createProduct(ProductCategory.minibar, true, 800L);
		createInventoryItem(withTwoItems, 10, true);
		createInventoryItem(withTwoItems, 10, true);
		Product stocked = createStockedProduct(800L, 10);

		for (Product product : List.of(withoutItem, withInactiveItem, withTwoItems)) {
			String orderId = createOrder(booking, List.of(stocked, product), List.of(1, 1));

			updateStatusExpectingBadRequest(orderId, "accepted",
					"Product " + product.getName() + " must have exactly one active inventory item; found "
							+ (product == withTwoItems ? 2 : 0));

			assertThat(orderRepository.findById(UUID.fromString(orderId)).orElseThrow().getStatus())
					.isEqualTo(OrderStatus.pending);
			assertThat(movementsOfOrder(orderId)).isEmpty();
		}
		assertThat(stockOf(stocked)).isEqualTo(10);
	}

	@Test
	void cancellingAfterAcceptanceRestoresStockExactlyOnce() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product burger = createStockedProduct(3500L, 10);
		Product soda = createStockedProduct(800L, 5);
		String orderId = createOrder(booking, List.of(burger, soda), List.of(3, 4));
		updateStatusExpectingOk(orderId, "accepted");
		updateStatusExpectingOk(orderId, "preparing");

		updateStatusExpectingOk(orderId, "cancelled");
		updateStatusExpectingBadRequest(orderId, "cancelled", "Room service order is already cancelled");

		assertThat(stockOf(burger)).isEqualTo(10);
		assertThat(stockOf(soda)).isEqualTo(5);
		List<InventoryMovement> returns = movementsOfOrder(orderId).stream()
				.filter(movement -> movement.getType() == InventoryMovementType.in)
				.toList();
		assertThat(returns).hasSize(2)
				.allSatisfy(movement ->
						assertThat(movement.getReason()).isEqualTo(InventoryMovementReason.room_service_return));
		assertThat(returns).extracting(InventoryMovement::getQuantity).containsExactlyInAnyOrder(3, 4);
		assertThat(orderRepository.findById(UUID.fromString(orderId)).orElseThrow().getInventoryRestoredAt())
				.isNotNull();
	}

	@Test
	void cancellingOrRejectingPendingOrderDoesNotTouchInventory() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createStockedProduct(1500L, 10);
		String cancelledId = createOrder(booking, product, 2);
		String rejectedId = createOrder(booking, product, 2);

		updateStatusExpectingOk(cancelledId, "cancelled");
		updateStatusExpectingOk(rejectedId, "rejected");

		assertThat(stockOf(product)).isEqualTo(10);
		assertThat(movementsOfOrder(cancelledId)).isEmpty();
		assertThat(movementsOfOrder(rejectedId)).isEmpty();
	}

	@Test
	void databaseRejectsDuplicateRoomServiceMovementsAndOrphanReturns() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createStockedProduct(1500L, 10);
		String orderId = createOrder(booking, product, 1);
		updateStatusExpectingOk(orderId, "accepted");
		Order order = orderRepository.findById(UUID.fromString(orderId)).orElseThrow();
		InventoryItem item = inventoryItemOf(product);

		InventoryMovement duplicate = roomServiceMovement(item, order, InventoryMovementType.out,
				InventoryMovementReason.sale);
		assertThatThrownBy(() -> inventoryMovementRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);

		InventoryMovement orphanReturn = roomServiceMovement(item, null, InventoryMovementType.in,
				InventoryMovementReason.room_service_return);
		assertThatThrownBy(() -> inventoryMovementRepository.saveAndFlush(orphanReturn))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(movementsOfOrder(orderId)).hasSize(1);
	}

	@Test
	void concurrentAcceptsOfDifferentOrdersNeverOversellStock() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createStockedProduct(1500L, 3);
		UUID firstOrder = UUID.fromString(createOrder(booking, product, 2));
		UUID secondOrder = UUID.fromString(createOrder(booking, product, 2));

		List<Boolean> outcomes = runConcurrently(List.of(
				() -> roomServiceOrderService.updateStatus(firstOrder, OrderStatus.accepted, null),
				() -> roomServiceOrderService.updateStatus(secondOrder, OrderStatus.accepted, null)));

		assertThat(outcomes).containsExactlyInAnyOrder(true, false);
		assertThat(stockOf(product)).isEqualTo(1);
		assertThat(movementsOfOrder(firstOrder.toString()).size()
				+ movementsOfOrder(secondOrder.toString()).size()).isEqualTo(1);
	}

	@Test
	void concurrentAcceptsOfSameOrderDeductOnlyOnce() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createStockedProduct(1500L, 10);
		UUID orderId = UUID.fromString(createOrder(booking, product, 2));

		List<Boolean> outcomes = runConcurrently(List.of(
				() -> roomServiceOrderService.updateStatus(orderId, OrderStatus.accepted, null),
				() -> roomServiceOrderService.updateStatus(orderId, OrderStatus.accepted, null)));

		assertThat(outcomes).containsExactlyInAnyOrder(true, false);
		assertThat(stockOf(product)).isEqualTo(8);
		assertThat(movementsOfOrder(orderId.toString())).hasSize(1);
	}

	@Test
	void concurrentCancelsOfSameOrderRestoreOnlyOnce() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createStockedProduct(1500L, 10);
		UUID orderId = UUID.fromString(createOrder(booking, product, 2));
		updateStatusExpectingOk(orderId.toString(), "accepted");

		List<Boolean> outcomes = runConcurrently(List.of(
				() -> roomServiceOrderService.updateStatus(orderId, OrderStatus.cancelled, null),
				() -> roomServiceOrderService.updateStatus(orderId, OrderStatus.cancelled, null)));

		assertThat(outcomes).containsExactlyInAnyOrder(true, false);
		assertThat(stockOf(product)).isEqualTo(10);
		assertThat(movementsOfOrder(orderId.toString())).hasSize(2);
	}

	// ---------- Folio

	@Test
	void deliveringPostsOneChargeWithFrozenOrderTotalAndStoresChargeId() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product burger = createStockedProduct(3500L, 10);
		Product soda = createStockedProduct(800L, 10);
		openFolio(booking);
		String orderId = createOrder(booking, List.of(burger, soda), List.of(2, 3));
		burger.setPriceCents(99999L);
		productRepository.save(burger);
		moveToOnTheWay(orderId);

		MvcResult result = mockMvc.perform(post("/api/v1/room-service/orders/{orderId}/status", orderId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "delivered"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("delivered"))
				.andExpect(jsonPath("$.totalCents").value(9400))
				.andExpect(jsonPath("$.chargeId").exists())
				.andReturn();

		String chargeId = JsonPath.read(result.getResponse().getContentAsString(), "$.chargeId");
		List<Charge> charges = chargeRepository.findByBookingIdOrderByChargedAtAscCreatedAtAsc(booking.getId());
		assertThat(charges).hasSize(1);
		Charge charge = charges.get(0);
		assertThat(charge.getId().toString()).isEqualTo(chargeId);
		assertThat(charge.getAmountCents()).isEqualTo(9400L);
		assertThat(charge.getCategory()).isEqualTo(ChargeCategory.consumption);
		assertThat(charge.getStatus()).isEqualTo(ChargeStatus.posted);
		assertThat(charge.getDescription()).contains(orderId);
		assertThat(guestAccountRepository.findByBookingId(booking.getId()).orElseThrow().getBalanceCents())
				.isEqualTo(9400L);
		assertThat(orderRepository.findById(UUID.fromString(orderId)).orElseThrow().getCharge().getId().toString())
				.isEqualTo(chargeId);

		mockMvc.perform(get("/api/v1/room-service/orders/{orderId}", orderId).with(staffUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.chargeId").value(chargeId));
	}

	@Test
	void deliveringAgainDoesNotCreateSecondCharge() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createStockedProduct(1500L, 10);
		openFolio(booking);
		String orderId = createOrder(booking, product, 1);
		moveToOnTheWay(orderId);
		updateStatusExpectingOk(orderId, "delivered");

		updateStatusExpectingBadRequest(orderId, "delivered", "Room service order is already delivered");

		assertThat(chargeRepository.findByBookingIdOrderByChargedAtAscCreatedAtAsc(booking.getId())).hasSize(1);
	}

	@Test
	void deliveringWithoutFolioReturnsNotFoundAndKeepsOrderOnTheWay() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createStockedProduct(1500L, 10);
		String orderId = createOrder(booking, product, 1);
		moveToOnTheWay(orderId);

		mockMvc.perform(post("/api/v1/room-service/orders/{orderId}/status", orderId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "delivered"}
								"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Guest account not found")));

		assertOnTheWayWithoutCharge(booking, orderId);
	}

	@Test
	void deliveringWithClosedFolioReturnsBadRequestAndKeepsOrderOnTheWay() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createStockedProduct(1500L, 10);
		openFolio(booking);
		String orderId = createOrder(booking, product, 1);
		moveToOnTheWay(orderId);
		GuestAccount account = guestAccountRepository.findByBookingId(booking.getId()).orElseThrow();
		account.setStatus(GuestAccountStatus.closed);
		guestAccountRepository.save(account);

		updateStatusExpectingBadRequest(orderId, "delivered", "Guest account is not open");

		assertOnTheWayWithoutCharge(booking, orderId);
		assertThat(guestAccountRepository.findByBookingId(booking.getId()).orElseThrow().getBalanceCents()).isZero();
	}

	@Test
	void zeroTotalOrderCannotBeDelivered() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product freeProduct = createStockedProduct(0L, 10);
		openFolio(booking);
		String orderId = createOrder(booking, freeProduct, 1);
		moveToOnTheWay(orderId);

		updateStatusExpectingBadRequest(orderId, "delivered",
				"Room service order total must be greater than zero to be delivered");

		assertOnTheWayWithoutCharge(booking, orderId);
	}

	@Test
	void concurrentDeliveriesOfSameOrderPostOnlyOneCharge() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createStockedProduct(1500L, 10);
		openFolio(booking);
		UUID orderId = UUID.fromString(createOrder(booking, product, 2));
		moveToOnTheWay(orderId.toString());

		List<Boolean> outcomes = runConcurrently(List.of(
				() -> roomServiceOrderService.updateStatus(orderId, OrderStatus.delivered, null),
				() -> roomServiceOrderService.updateStatus(orderId, OrderStatus.delivered, null)));

		assertThat(outcomes).containsExactlyInAnyOrder(true, false);
		List<Charge> charges = chargeRepository.findByBookingIdOrderByChargedAtAscCreatedAtAsc(booking.getId());
		assertThat(charges).hasSize(1);
		assertThat(charges.get(0).getAmountCents()).isEqualTo(3000L);
		assertThat(guestAccountRepository.findByBookingId(booking.getId()).orElseThrow().getBalanceCents())
				.isEqualTo(3000L);
	}

	@Test
	void databaseRejectsLinkingOneChargeToTwoOrders() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createStockedProduct(1500L, 10);
		openFolio(booking);
		String deliveredId = createOrder(booking, product, 1);
		moveToOnTheWay(deliveredId);
		updateStatusExpectingOk(deliveredId, "delivered");
		Charge charge = orderRepository.findById(UUID.fromString(deliveredId)).orElseThrow().getCharge();
		Order other = orderRepository.findById(UUID.fromString(createOrder(booking, product, 1))).orElseThrow();

		other.setCharge(charge);
		assertThatThrownBy(() -> orderRepository.saveAndFlush(other))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void invalidStatusTransitionReturnsBadRequest() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createProduct(ProductCategory.food_and_beverage, true, 1500L);
		String orderId = createOrder(booking, product, 1);

		mockMvc.perform(post("/api/v1/room-service/orders/{orderId}/status", orderId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "ready"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith("Invalid room service order status transition")));
	}

	@Test
	void terminalStatusCannotChange() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createProduct(ProductCategory.food_and_beverage, true, 1500L);
		String orderId = createOrder(booking, product, 1);
		Order order = orderRepository.findById(UUID.fromString(orderId)).orElseThrow();
		order.setStatus(OrderStatus.delivered);
		orderRepository.save(order);

		mockMvc.perform(post("/api/v1/room-service/orders/{orderId}/status", orderId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "accepted"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith("Room service order status is terminal")));
	}

	@Test
	void invalidUuidAndEnumReturnBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/room-service/orders/{orderId}", "ORD-1").with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));

		mockMvc.perform(get("/api/v1/room-service/products")
						.param("category", "spa")
						.with(staffUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	private Booking createRoomServiceBooking() {
		return createRoomServiceBooking(BookingStatus.checked_in);
	}

	private Booking createRoomServiceBooking(BookingStatus status) {
		Guest guest = createGuest();
		RoomType roomType = createRoomType();
		Room room = createRoom(roomType);
		Rate rate = createRate(roomType);
		Booking booking = createBooking(guest, roomType, room, rate);
		booking.setStatus(status);
		return bookingRepository.save(booking);
	}

	private void updateStatusExpectingBadRequest(String orderId, String status, String messagePrefix) throws Exception {
		mockMvc.perform(post("/api/v1/room-service/orders/{orderId}/status", orderId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "%s"}
								""".formatted(status)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(startsWith(messagePrefix)));
	}

	private Product createProduct(ProductCategory category, boolean active, long priceCents) {
		Product product = new Product();
		product.setSku("SKU-" + uniqueSuffix());
		product.setName("Product " + uniqueSuffix());
		product.setDescription("Room service product");
		product.setCategory(category);
		product.setPriceCents(priceCents);
		product.setCurrency("GTQ");
		product.setStockQuantity(10);
		product.setReorderLevel(2);
		product.setActive(active);
		product.setCreatedAt(now());
		product.setUpdatedAt(now());
		product = productRepository.save(product);
		productIds.add(product.getId());
		return product;
	}

	private String createOrder(Booking booking, Product product, int quantity) throws Exception {
		return createOrder(booking, List.of(product), List.of(quantity));
	}

	private String createOrder(Booking booking, List<Product> products, List<Integer> quantities) throws Exception {
		List<String> items = new ArrayList<>();
		for (int i = 0; i < products.size(); i++) {
			items.add("""
					{"productId": "%s", "quantity": %d}""".formatted(products.get(i).getId(), quantities.get(i)));
		}
		MvcResult result = mockMvc.perform(post("/api/v1/room-service/orders")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookingId": "%s", "items": [%s]}
								""".formatted(booking.getId(), String.join(",", items))))
				.andExpect(status().isCreated())
				.andReturn();
		return trackCreatedOrder(result).toString();
	}

	private void openFolio(Booking booking) throws Exception {
		mockMvc.perform(post("/api/v1/bookings/{bookingId}/folio/open", booking.getId()).with(staffUser()))
				.andExpect(status().isCreated());
		folioBookingIds.add(booking.getId());
	}

	private void moveToOnTheWay(String orderId) throws Exception {
		for (String status : List.of("accepted", "preparing", "ready", "on_the_way")) {
			updateStatusExpectingOk(orderId, status);
		}
	}

	private void assertOnTheWayWithoutCharge(Booking booking, String orderId) {
		Order order = orderRepository.findById(UUID.fromString(orderId)).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.on_the_way);
		assertThat(order.getCharge()).isNull();
		assertThat(chargeRepository.findByBookingIdOrderByChargedAtAscCreatedAtAsc(booking.getId())).isEmpty();
	}

	/** Producto activo con exactamente un artículo de inventario activo vinculado. */
	private Product createStockedProduct(long priceCents, int stock) {
		Product product = createProduct(ProductCategory.food_and_beverage, true, priceCents);
		createInventoryItem(product, stock, true);
		return product;
	}

	private InventoryItem createInventoryItem(Product product, int stock, boolean active) {
		InventoryItem item = new InventoryItem();
		item.setSku("INV-" + uniqueSuffix());
		item.setName("Item " + product.getName());
		item.setCategory("room-service-test");
		item.setUnit("unit");
		item.setCurrentQuantity(stock);
		item.setMinimumQuantity(0);
		item.setProduct(product);
		item.setActive(active);
		item.setCreatedAt(now());
		item.setUpdatedAt(now());
		item = inventoryItemRepository.save(item);
		inventoryItemIds.add(item.getId());
		return item;
	}

	private InventoryItem inventoryItemOf(Product product) {
		UUID itemId = inventoryItemRepository.findActiveIdsByProductId(product.getId()).get(0);
		return inventoryItemRepository.findById(itemId).orElseThrow();
	}

	private int stockOf(Product product) {
		return inventoryItemOf(product).getCurrentQuantity();
	}

	private List<InventoryMovement> movementsOfOrder(String orderId) {
		UUID id = UUID.fromString(orderId);
		List<InventoryMovement> movements = new ArrayList<>();
		movements.addAll(inventoryMovementRepository.findByRoomServiceOrderIdAndType(id, InventoryMovementType.out));
		movements.addAll(inventoryMovementRepository.findByRoomServiceOrderIdAndType(id, InventoryMovementType.in));
		return movements;
	}

	private static InventoryMovement roomServiceMovement(
			InventoryItem item,
			Order order,
			InventoryMovementType type,
			InventoryMovementReason reason
	) {
		InventoryMovement movement = new InventoryMovement();
		movement.setInventoryItem(item);
		movement.setRoomServiceOrder(order);
		movement.setType(type);
		movement.setReason(reason);
		movement.setQuantity(1);
		movement.setOccurredAt(now());
		movement.setCreatedAt(now());
		return movement;
	}

	/**
	 * Lanza las operaciones a la vez y devuelve, por cada una, si terminó bien
	 * (true) o fue rechazada con 400 (false).
	 */
	private static List<Boolean> runConcurrently(List<Callable<?>> operations) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(operations.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for (Callable<?> operation : operations) {
				futures.add(executor.submit(() -> {
					start.await(2, TimeUnit.SECONDS);
					return operation.call();
				}));
			}
			start.countDown();

			List<Boolean> outcomes = new ArrayList<>();
			for (Future<?> future : futures) {
				try {
					future.get(10, TimeUnit.SECONDS);
					outcomes.add(true);
				} catch (ExecutionException exception) {
					assertThat(exception.getCause()).isInstanceOf(BadRequestException.class);
					outcomes.add(false);
				}
			}
			return outcomes;
		} finally {
			executor.shutdownNow();
		}
	}

	private UUID trackCreatedOrder(MvcResult result) throws Exception {
		String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		UUID orderId = UUID.fromString(id);
		orderIds.add(orderId);
		return orderId;
	}

	private void createOrderWithProductExpectingNotFound(Booking booking, UUID productId) throws Exception {
		mockMvc.perform(post("/api/v1/room-service/orders")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookingId": "%s", "items": [{"productId": "%s", "quantity": 1}]}
								""".formatted(booking.getId(), productId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(startsWith("Product not found")));
	}

	private void updateStatusExpectingOk(String orderId, String status) throws Exception {
		mockMvc.perform(post("/api/v1/room-service/orders/{orderId}/status", orderId)
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"status": "%s"}
								""".formatted(status)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(orderId))
				.andExpect(jsonPath("$.status").value(status));
	}
}
