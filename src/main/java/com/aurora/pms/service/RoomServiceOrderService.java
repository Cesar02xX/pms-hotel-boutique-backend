package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreateRoomServiceOrderRequest;
import com.aurora.pms.dto.response.RoomServiceOrderResponse;
import com.aurora.pms.dto.response.RoomServiceProductResponse;
import com.aurora.pms.model.enums.OrderStatus;
import com.aurora.pms.model.enums.ProductCategory;

public interface RoomServiceOrderService {

	List<RoomServiceProductResponse> findProducts(ProductCategory category);

	List<RoomServiceOrderResponse> findOrders(UUID bookingId, OrderStatus status);

	RoomServiceOrderResponse findOrderById(UUID orderId);

	RoomServiceOrderResponse createOrder(CreateRoomServiceOrderRequest request);

	RoomServiceOrderResponse updateStatus(UUID orderId, OrderStatus status, String actorEmail);

	/** {@code notes} null conserva las notas actuales; vacio las limpia. */
	RoomServiceOrderResponse updateStatus(UUID orderId, OrderStatus status, String notes, String actorEmail);

	RoomServiceOrderResponse updateNotes(UUID orderId, String notes);
}
