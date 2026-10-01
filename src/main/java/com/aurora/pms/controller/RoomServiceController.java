package com.aurora.pms.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.request.CreateRoomServiceOrderRequest;
import com.aurora.pms.dto.request.UpdateRoomServiceOrderStatusRequest;
import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.RoomServiceOrderResponse;
import com.aurora.pms.dto.response.RoomServiceProductResponse;
import com.aurora.pms.model.enums.OrderStatus;
import com.aurora.pms.model.enums.ProductCategory;
import com.aurora.pms.service.RoomServiceOrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/room-service")
@Tag(name = "Room Service", description = "Catalogo y pedidos de Room Service")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class RoomServiceController {

	private final RoomServiceOrderService roomServiceOrderService;

	public RoomServiceController(RoomServiceOrderService roomServiceOrderService) {
		this.roomServiceOrderService = roomServiceOrderService;
	}

	@GetMapping("/products")
	@Operation(summary = "List active room service products")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Products found"),
			@ApiResponse(responseCode = "400", description = "Invalid category",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<RoomServiceProductResponse>> findProducts(
			@RequestParam(required = false) ProductCategory category
	) {
		return ResponseEntity.ok(roomServiceOrderService.findProducts(category));
	}

	@GetMapping("/orders")
	@Operation(summary = "List room service orders")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Orders found"),
			@ApiResponse(responseCode = "400", description = "Invalid filter",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<List<RoomServiceOrderResponse>> findOrders(
			@RequestParam(required = false) UUID bookingId,
			@RequestParam(required = false) OrderStatus status
	) {
		return ResponseEntity.ok(roomServiceOrderService.findOrders(bookingId, status));
	}

	@GetMapping("/orders/{orderId}")
	@Operation(summary = "Get a room service order")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Order found"),
			@ApiResponse(responseCode = "400", description = "Invalid id",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Order not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<RoomServiceOrderResponse> findOrderById(@PathVariable UUID orderId) {
		return ResponseEntity.ok(roomServiceOrderService.findOrderById(orderId));
	}

	@PostMapping("/orders")
	@Operation(summary = "Create a room service order")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Order created"),
			@ApiResponse(responseCode = "400", description = "Invalid request or booking is not checked_in",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Booking or product not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<RoomServiceOrderResponse> createOrder(
			@Valid @RequestBody CreateRoomServiceOrderRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(roomServiceOrderService.createOrder(request));
	}

	@PostMapping("/orders/{orderId}/status")
	@Operation(summary = "Update a room service order status",
			description = "Allowed flow: pending -> accepted -> preparing -> ready -> on_the_way -> delivered. "
					+ "Orders can be cancelled from pending, accepted, preparing or ready; "
					+ "pending can also be rejected.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Order status updated"),
			@ApiResponse(responseCode = "400", description = "Invalid status transition",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "Order not found",
					content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
	})
	public ResponseEntity<RoomServiceOrderResponse> updateStatus(
			@PathVariable UUID orderId,
			@Valid @RequestBody UpdateRoomServiceOrderStatusRequest request
	) {
		return ResponseEntity.ok(roomServiceOrderService.updateStatus(orderId, request.status()));
	}
}
