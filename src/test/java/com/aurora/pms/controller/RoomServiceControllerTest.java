package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Guest;
import com.aurora.pms.model.Order;
import com.aurora.pms.model.OrderItem;
import com.aurora.pms.model.Product;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.Room;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.OrderStatus;
import com.aurora.pms.model.enums.ProductCategory;
import com.aurora.pms.repository.OrderItemRepository;
import com.aurora.pms.repository.OrderRepository;
import com.aurora.pms.repository.ProductRepository;
import com.jayway.jsonpath.JsonPath;

class RoomServiceControllerTest extends AbstractCatalogApiTest {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderItemRepository orderItemRepository;

	private final List<UUID> orderIds = new ArrayList<>();
	private final List<UUID> productIds = new ArrayList<>();

	@AfterEach
	void cleanUpRoomServiceData() {
		orderIds.forEach(orderId -> orderItemRepository.deleteAll(orderItemRepository.findByOrderIdOrderById(orderId)));
		orderRepository.deleteAllById(orderIds);
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
		Product product = createProduct(ProductCategory.food_and_beverage, true, 1500L);
		String orderId = createOrder(booking, product, 1);

		updateStatusExpectingOk(orderId, "accepted");
		updateStatusExpectingOk(orderId, "preparing");
		updateStatusExpectingOk(orderId, "ready");
		updateStatusExpectingOk(orderId, "on_the_way");
		updateStatusExpectingOk(orderId, "delivered");
	}

	@Test
	void pendingCanBeRejectedAndOrdersCanBeCancelledBeforeDelivered() throws Exception {
		Booking booking = createRoomServiceBooking();
		Product product = createProduct(ProductCategory.food_and_beverage, true, 1500L);
		String rejectedOrderId = createOrder(booking, product, 1);
		String cancelledOrderId = createOrder(booking, product, 1);
		updateStatusExpectingOk(cancelledOrderId, "accepted");

		updateStatusExpectingOk(rejectedOrderId, "rejected");
		updateStatusExpectingOk(cancelledOrderId, "cancelled");
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
		Guest guest = createGuest();
		RoomType roomType = createRoomType();
		Room room = createRoom(roomType);
		Rate rate = createRate(roomType);
		return createBooking(guest, roomType, room, rate);
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
		MvcResult result = mockMvc.perform(post("/api/v1/room-service/orders")
						.with(staffUser())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"bookingId": "%s", "items": [{"productId": "%s", "quantity": %d}]}
								""".formatted(booking.getId(), product.getId(), quantity)))
				.andExpect(status().isCreated())
				.andReturn();
		return trackCreatedOrder(result).toString();
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
