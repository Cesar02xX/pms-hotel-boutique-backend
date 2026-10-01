package com.aurora.pms.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.CreateGuestRoomServiceOrderRequest;
import com.aurora.pms.dto.request.CreateGuestServiceRequest;
import com.aurora.pms.dto.request.GuestLinkRequest;
import com.aurora.pms.dto.response.ConciergeRequestResponse;
import com.aurora.pms.dto.response.AmenityResponse;
import com.aurora.pms.dto.response.GuestLinkResponse;
import com.aurora.pms.dto.response.GuestNotificationResponse;
import com.aurora.pms.dto.response.GuestStayResponse;
import com.aurora.pms.dto.response.RoomServiceOrderResponse;
import com.aurora.pms.dto.response.StayoverCleaningResponse;
import com.aurora.pms.security.GuestPrincipal;
import com.aurora.pms.service.GuestAccessService;
import com.aurora.pms.service.AdminCatalogService;
import com.aurora.pms.service.GuestNotificationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/guest")
public class GuestAccessController {

	private final GuestAccessService guestAccessService;
	private final AdminCatalogService adminCatalogService;
	private final GuestNotificationService notificationService;

	public GuestAccessController(
			GuestAccessService guestAccessService,
			AdminCatalogService adminCatalogService,
			GuestNotificationService notificationService
	) {
		this.guestAccessService = guestAccessService;
		this.adminCatalogService = adminCatalogService;
		this.notificationService = notificationService;
	}

	@PostMapping("/auth/link")
	public ResponseEntity<GuestLinkResponse> link(@Valid @RequestBody GuestLinkRequest request) {
		return ResponseEntity.ok(guestAccessService.link(request.code()));
	}

	@GetMapping("/stay")
	public ResponseEntity<GuestStayResponse> stay(@AuthenticationPrincipal GuestPrincipal guest) {
		return ResponseEntity.ok(guestAccessService.getStay(guest.bookingId()));
	}

	@GetMapping("/amenities")
	public ResponseEntity<List<AmenityResponse>> amenities() {
		return ResponseEntity.ok(adminCatalogService.findAmenities(true));
	}

	@GetMapping("/amenities/{amenityId}")
	public ResponseEntity<AmenityResponse> amenity(@PathVariable UUID amenityId) {
		AmenityResponse amenity = adminCatalogService.findAmenity(amenityId);
		if (!Boolean.TRUE.equals(amenity.active())) {
			throw new org.springframework.security.access.AccessDeniedException("Guest cannot access inactive amenity");
		}
		return ResponseEntity.ok(amenity);
	}

	@PostMapping("/room-service/orders")
	public ResponseEntity<RoomServiceOrderResponse> createRoomServiceOrder(
			@AuthenticationPrincipal GuestPrincipal guest,
			@Valid @RequestBody CreateGuestRoomServiceOrderRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(guestAccessService.createRoomServiceOrder(guest.bookingId(), request));
	}

	@GetMapping("/room-service/orders")
	public ResponseEntity<List<RoomServiceOrderResponse>> roomServiceOrders(@AuthenticationPrincipal GuestPrincipal guest) {
		return ResponseEntity.ok(guestAccessService.findRoomServiceOrders(guest.bookingId()));
	}

	@GetMapping("/room-service/orders/{orderId}")
	public ResponseEntity<RoomServiceOrderResponse> roomServiceOrder(
			@AuthenticationPrincipal GuestPrincipal guest,
			@PathVariable UUID orderId
	) {
		return ResponseEntity.ok(guestAccessService.findRoomServiceOrder(guest.bookingId(), orderId));
	}

	@PostMapping("/room-service/orders/{orderId}/cancel")
	public ResponseEntity<RoomServiceOrderResponse> cancelRoomServiceOrder(
			@AuthenticationPrincipal GuestPrincipal guest,
			@PathVariable UUID orderId
	) {
		return ResponseEntity.ok(guestAccessService.cancelRoomServiceOrder(guest.bookingId(), orderId));
	}

	@PostMapping("/housekeeping/requests")
	public ResponseEntity<StayoverCleaningResponse> createHousekeepingRequest(
			@AuthenticationPrincipal GuestPrincipal guest,
			@Valid @RequestBody CreateGuestServiceRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(guestAccessService.createHousekeepingRequest(guest.bookingId(), request));
	}

	@GetMapping("/housekeeping/requests")
	public ResponseEntity<List<StayoverCleaningResponse>> housekeepingRequests(@AuthenticationPrincipal GuestPrincipal guest) {
		return ResponseEntity.ok(guestAccessService.findHousekeepingRequests(guest.bookingId()));
	}

	@PostMapping("/housekeeping/requests/{requestId}/cancel")
	public ResponseEntity<StayoverCleaningResponse> cancelHousekeepingRequest(
			@AuthenticationPrincipal GuestPrincipal guest,
			@PathVariable UUID requestId
	) {
		return ResponseEntity.ok(guestAccessService.cancelHousekeepingRequest(guest.bookingId(), requestId));
	}

	@PostMapping("/concierge/requests")
	public ResponseEntity<ConciergeRequestResponse> createConciergeRequest(
			@AuthenticationPrincipal GuestPrincipal guest,
			@Valid @RequestBody CreateGuestServiceRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(guestAccessService.createConciergeRequest(guest.bookingId(), request));
	}

	@GetMapping("/concierge/requests")
	public ResponseEntity<List<ConciergeRequestResponse>> conciergeRequests(@AuthenticationPrincipal GuestPrincipal guest) {
		return ResponseEntity.ok(guestAccessService.findConciergeRequests(guest.bookingId()));
	}

	@GetMapping("/concierge/requests/{requestId}")
	public ResponseEntity<ConciergeRequestResponse> conciergeRequest(
			@AuthenticationPrincipal GuestPrincipal guest,
			@PathVariable UUID requestId
	) {
		return ResponseEntity.ok(guestAccessService.findConciergeRequest(guest.bookingId(), requestId));
	}

	@PostMapping("/concierge/requests/{requestId}/cancel")
	public ResponseEntity<ConciergeRequestResponse> cancelConciergeRequest(
			@AuthenticationPrincipal GuestPrincipal guest,
			@PathVariable UUID requestId
	) {
		return ResponseEntity.ok(guestAccessService.cancelConciergeRequest(guest.bookingId(), requestId));
	}

	@GetMapping("/notifications")
	public ResponseEntity<List<GuestNotificationResponse>> notifications(@AuthenticationPrincipal GuestPrincipal guest) {
		return ResponseEntity.ok(notificationService.findOwn(guest.bookingId()));
	}

	@GetMapping("/notifications/unread-count")
	public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal GuestPrincipal guest) {
		return ResponseEntity.ok(Map.of("unreadCount", notificationService.countUnread(guest.bookingId())));
	}

	@PostMapping("/notifications/{notificationId}/read")
	public ResponseEntity<GuestNotificationResponse> markRead(
			@AuthenticationPrincipal GuestPrincipal guest,
			@PathVariable UUID notificationId
	) {
		return ResponseEntity.ok(notificationService.markRead(guest.bookingId(), notificationId));
	}
}
