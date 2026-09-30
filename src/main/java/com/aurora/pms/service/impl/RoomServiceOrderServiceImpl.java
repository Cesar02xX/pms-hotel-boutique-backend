package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateRoomServiceOrderItemRequest;
import com.aurora.pms.dto.request.CreateRoomServiceOrderRequest;
import com.aurora.pms.dto.response.RoomServiceOrderResponse;
import com.aurora.pms.dto.response.RoomServiceProductResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.RoomServiceMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Order;
import com.aurora.pms.model.OrderItem;
import com.aurora.pms.model.Product;
import com.aurora.pms.model.enums.OrderStatus;
import com.aurora.pms.model.enums.ProductCategory;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.OrderItemRepository;
import com.aurora.pms.repository.OrderRepository;
import com.aurora.pms.repository.ProductRepository;
import com.aurora.pms.service.RoomServiceOrderService;

@Service
public class RoomServiceOrderServiceImpl implements RoomServiceOrderService {

	private static final Set<OrderStatus> TERMINAL_STATUSES = Set.of(
			OrderStatus.delivered,
			OrderStatus.rejected,
			OrderStatus.cancelled
	);

	private final ProductRepository productRepository;
	private final BookingRepository bookingRepository;
	private final OrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final RoomServiceMapper roomServiceMapper;

	public RoomServiceOrderServiceImpl(
			ProductRepository productRepository,
			BookingRepository bookingRepository,
			OrderRepository orderRepository,
			OrderItemRepository orderItemRepository,
			RoomServiceMapper roomServiceMapper
	) {
		this.productRepository = productRepository;
		this.bookingRepository = bookingRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.roomServiceMapper = roomServiceMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoomServiceProductResponse> findProducts(ProductCategory category) {
		List<Product> products = category == null
				? productRepository.findByActiveTrueOrderByNameAsc()
				: productRepository.findByActiveTrueAndCategoryOrderByNameAsc(category);

		return products.stream()
				.map(roomServiceMapper::toProductResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoomServiceOrderResponse> findOrders(UUID bookingId, OrderStatus status) {
		List<Order> orders = orderRepository.findWithFilters(bookingId, status);
		Map<UUID, List<OrderItem>> itemsByOrderId = findItemsByOrderId(orders);

		return orders.stream()
				.map(order -> roomServiceMapper.toOrderResponse(
						order,
						itemsByOrderId.getOrDefault(order.getId(), List.of())
				))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public RoomServiceOrderResponse findOrderById(UUID orderId) {
		Order order = getOrder(orderId);
		return roomServiceMapper.toOrderResponse(order, orderItemRepository.findByOrderIdOrderById(orderId));
	}

	@Override
	@Transactional
	public RoomServiceOrderResponse createOrder(CreateRoomServiceOrderRequest request) {
		Booking booking = bookingRepository.findById(request.bookingId())
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + request.bookingId()));

		Order order = roomServiceMapper.toOrderEntity(request, booking);
		OffsetDateTime now = OffsetDateTime.now();
		order.setRequestedAt(now);
		order.setCreatedAt(now);
		order.setUpdatedAt(now);
		order = orderRepository.save(order);

		Order persistedOrder = order;
		List<OrderItem> items = request.items().stream()
				.map(itemRequest -> toOrderItem(persistedOrder, itemRequest))
				.toList();
		items = orderItemRepository.saveAll(items);

		return roomServiceMapper.toOrderResponse(order, items);
	}

	@Override
	@Transactional
	public RoomServiceOrderResponse updateStatus(UUID orderId, OrderStatus status) {
		Order order = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new ResourceNotFoundException("Room service order not found: " + orderId));
		validateTransition(order.getStatus(), status);

		order.setStatus(status);
		order.setUpdatedAt(OffsetDateTime.now());
		order = orderRepository.save(order);

		return roomServiceMapper.toOrderResponse(order, orderItemRepository.findByOrderIdOrderById(orderId));
	}

	private OrderItem toOrderItem(Order order, CreateRoomServiceOrderItemRequest itemRequest) {
		Product product = productRepository.findByIdAndActiveTrue(itemRequest.productId())
				.orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemRequest.productId()));
		return roomServiceMapper.toOrderItemEntity(order, product, itemRequest.quantity());
	}

	private Order getOrder(UUID orderId) {
		return orderRepository.findById(orderId)
				.orElseThrow(() -> new ResourceNotFoundException("Room service order not found: " + orderId));
	}

	private Map<UUID, List<OrderItem>> findItemsByOrderId(List<Order> orders) {
		if (orders.isEmpty()) {
			return Map.of();
		}
		List<UUID> orderIds = orders.stream().map(Order::getId).toList();
		return orderItemRepository.findByOrderIdIn(orderIds).stream()
				.collect(Collectors.groupingBy(item -> item.getOrder().getId()));
	}

	private void validateTransition(OrderStatus current, OrderStatus next) {
		if (current == next) {
			throw new BadRequestException("Room service order is already " + current);
		}
		if (TERMINAL_STATUSES.contains(current)) {
			throw new BadRequestException("Room service order status is terminal: " + current);
		}
		if (!isAllowedTransition(current, next)) {
			throw new BadRequestException("Invalid room service order status transition: " + current + " -> " + next);
		}
	}

	private boolean isAllowedTransition(OrderStatus current, OrderStatus next) {
		return switch (current) {
			case pending -> next == OrderStatus.accepted
					|| next == OrderStatus.rejected
					|| next == OrderStatus.cancelled;
			case accepted -> next == OrderStatus.preparing || next == OrderStatus.cancelled;
			case preparing -> next == OrderStatus.ready || next == OrderStatus.cancelled;
			case ready -> next == OrderStatus.on_the_way || next == OrderStatus.cancelled;
			case on_the_way -> next == OrderStatus.delivered || next == OrderStatus.cancelled;
			case delivered, rejected, cancelled -> false;
		};
	}
}
