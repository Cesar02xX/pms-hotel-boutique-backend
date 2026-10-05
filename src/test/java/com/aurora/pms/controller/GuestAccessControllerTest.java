package com.aurora.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.GuestNotification;
import com.aurora.pms.model.Product;
import com.aurora.pms.model.RoomType;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.ProductCategory;
import com.aurora.pms.repository.GuestNotificationRepository;
import com.aurora.pms.repository.ProductRepository;
import com.aurora.pms.security.GuestPrincipal;

/** Endpoints del portal del huesped agregados para INT-12. */
class GuestAccessControllerTest extends AbstractCatalogApiTest {

	private static final String BASE_PATH = "/api/v1/guest";

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private GuestNotificationRepository notificationRepository;

	private final List<UUID> productIds = new ArrayList<>();
	private final List<UUID> notificationIds = new ArrayList<>();

	@AfterEach
	void cleanUpGuestAccessData() {
		notificationRepository.deleteAllById(notificationIds);
		productRepository.deleteAllById(productIds);
		notificationIds.clear();
		productIds.clear();
	}

	// ---------- Menu de Room Service

	@Test
	void guestProductsListOnlyActiveProducts() throws Exception {
		Booking booking = createCheckedInBooking();
		Product active = createProduct(ProductCategory.food_and_beverage, true);
		Product inactive = createProduct(ProductCategory.food_and_beverage, false);

		mockMvc.perform(get(BASE_PATH + "/room-service/products").with(guestOf(booking)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(active.getId().toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(inactive.getId().toString()))))
				.andExpect(jsonPath("$[*].active", everyItem(is(true))));
	}

	@Test
	void guestProductsFilterByCategory() throws Exception {
		Booking booking = createCheckedInBooking();
		Product minibar = createProduct(ProductCategory.minibar, true);
		Product food = createProduct(ProductCategory.food_and_beverage, true);

		mockMvc.perform(get(BASE_PATH + "/room-service/products")
						.param("category", "minibar")
						.with(guestOf(booking)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(minibar.getId().toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(food.getId().toString()))));
	}

	@Test
	void guestProductsRequireGuestIdentity() throws Exception {
		mockMvc.perform(get(BASE_PATH + "/room-service/products"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get(BASE_PATH + "/room-service/products").with(staffUser()))
				.andExpect(status().isForbidden());
	}

	// ---------- Marcar todas como leidas

	@Test
	void readAllMarksOnlyNotificationsOfTheTokenBooking() throws Exception {
		Booking own = createCheckedInBooking();
		Booking other = createCheckedInBooking();
		createNotification(own, "room_service_accepted");
		createNotification(own, "concierge_accepted");
		GuestNotification foreign = createNotification(other, "room_service_accepted");

		mockMvc.perform(post(BASE_PATH + "/notifications/read-all").with(guestOf(own)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[*].read", everyItem(is(true))));

		mockMvc.perform(get(BASE_PATH + "/notifications/unread-count").with(guestOf(own)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(0));
		assertThat(notificationRepository.findById(foreign.getId()).orElseThrow().getReadAt())
				.as("otra reserva no se toca")
				.isNull();
	}

	@Test
	void readAllWithoutUnreadNotificationsIsIdempotent() throws Exception {
		Booking booking = createCheckedInBooking();

		mockMvc.perform(post(BASE_PATH + "/notifications/read-all").with(guestOf(booking)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(post(BASE_PATH + "/notifications/read-all").with(guestOf(booking)))
				.andExpect(status().isOk());
	}

	@Test
	void readAllRequiresGuestIdentity() throws Exception {
		mockMvc.perform(post(BASE_PATH + "/notifications/read-all"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post(BASE_PATH + "/notifications/read-all").with(staffUser()))
				.andExpect(status().isForbidden());
	}

	// ---------- Helpers

	private RequestPostProcessor guestOf(Booking booking) {
		return user(new GuestPrincipal(booking.getId(), booking.getGuest().getId(), booking.getGuestLinkCode()));
	}

	private Booking createCheckedInBooking() {
		RoomType roomType = createRoomType();
		Booking booking = createBooking(createGuest(), roomType, createRoom(roomType), createRate(roomType));
		booking.setStatus(BookingStatus.checked_in);
		bookingRepository.save(booking);
		return booking;
	}

	private Product createProduct(ProductCategory category, boolean active) {
		Product product = new Product();
		product.setSku("SKU-" + uniqueSuffix());
		product.setName("Product " + uniqueSuffix());
		product.setDescription("Guest menu product");
		product.setCategory(category);
		product.setPriceCents(2500L);
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

	private GuestNotification createNotification(Booking booking, String type) {
		GuestNotification notification = new GuestNotification();
		notification.setBooking(booking);
		notification.setGuest(booking.getGuest());
		notification.setType(type);
		notification.setTitle("Room Service");
		notification.setMessage("Your request changed");
		notification.setResourceType("room_service_order");
		notification.setResourceId(UUID.randomUUID());
		notification.setCreatedAt(now());
		notification = notificationRepository.save(notification);
		notificationIds.add(notification.getId());
		return notification;
	}
}
