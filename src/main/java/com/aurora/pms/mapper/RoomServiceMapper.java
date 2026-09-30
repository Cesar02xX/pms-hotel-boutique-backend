package com.aurora.pms.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.CreateRoomServiceOrderRequest;
import com.aurora.pms.dto.response.RoomServiceOrderItemResponse;
import com.aurora.pms.dto.response.RoomServiceOrderResponse;
import com.aurora.pms.dto.response.RoomServiceProductResponse;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Order;
import com.aurora.pms.model.OrderItem;
import com.aurora.pms.model.Product;
import com.aurora.pms.model.enums.OrderStatus;

@Component
public class RoomServiceMapper {

	public RoomServiceProductResponse toProductResponse(Product product) {
		return new RoomServiceProductResponse(
				product.getId(),
				product.getSku(),
				product.getName(),
				product.getDescription(),
				product.getCategory(),
				product.getPriceCents(),
				product.getCurrency(),
				product.getActive()
		);
	}

	public RoomServiceOrderResponse toOrderResponse(Order order, List<OrderItem> items) {
		List<RoomServiceOrderItemResponse> itemResponses = items.stream()
				.map(this::toOrderItemResponse)
				.toList();
		long totalCents = itemResponses.stream()
				.mapToLong(RoomServiceOrderItemResponse::lineTotalCents)
				.sum();

		return new RoomServiceOrderResponse(
				order.getId(),
				order.getBooking().getId(),
				order.getRoom() != null ? order.getRoom().getId() : null,
				order.getGuest() != null ? order.getGuest().getId() : null,
				order.getStatus(),
				order.getNotes(),
				order.getCurrency(),
				totalCents,
				itemResponses,
				order.getRequestedAt(),
				order.getCreatedAt(),
				order.getUpdatedAt()
		);
	}

	public RoomServiceOrderItemResponse toOrderItemResponse(OrderItem item) {
		long lineTotalCents = item.getQuantity().longValue() * item.getUnitPriceCents();
		return new RoomServiceOrderItemResponse(
				item.getId(),
				item.getProduct().getId(),
				item.getProduct().getName(),
				item.getQuantity(),
				item.getUnitPriceCents(),
				lineTotalCents
		);
	}

	public Order toOrderEntity(CreateRoomServiceOrderRequest request, Booking booking) {
		Order order = new Order();
		order.setBooking(booking);
		order.setRoom(booking.getRoom());
		order.setGuest(booking.getGuest());
		order.setStatus(OrderStatus.pending);
		order.setNotes(request.notes());
		order.setCurrency("GTQ");
		return order;
	}

	public OrderItem toOrderItemEntity(Order order, Product product, Integer quantity) {
		OrderItem item = new OrderItem();
		item.setOrder(order);
		item.setProduct(product);
		item.setQuantity(quantity);
		item.setUnitPriceCents(product.getPriceCents());
		return item;
	}
}
