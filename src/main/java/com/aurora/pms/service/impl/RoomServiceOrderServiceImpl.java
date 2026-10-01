package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.request.CreateChargeRequest;
import com.aurora.pms.dto.request.CreateRoomServiceOrderItemRequest;
import com.aurora.pms.dto.request.CreateRoomServiceOrderRequest;
import com.aurora.pms.dto.response.ChargeResponse;
import com.aurora.pms.dto.response.RoomServiceOrderResponse;
import com.aurora.pms.dto.response.RoomServiceProductResponse;
import com.aurora.pms.exception.BadRequestException;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.mapper.RoomServiceMapper;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Order;
import com.aurora.pms.model.OrderItem;
import com.aurora.pms.model.Product;
import com.aurora.pms.model.User;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.ChargeCategory;
import com.aurora.pms.model.enums.OrderStatus;
import com.aurora.pms.model.enums.ProductCategory;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.ChargeRepository;
import com.aurora.pms.repository.OrderItemRepository;
import com.aurora.pms.repository.OrderRepository;
import com.aurora.pms.repository.ProductRepository;
import com.aurora.pms.repository.UserRepository;
import com.aurora.pms.service.GuestFolioService;
import com.aurora.pms.service.GuestNotificationService;
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
	private final UserRepository userRepository;
	private final RoomServiceMapper roomServiceMapper;
	private final RoomServiceOrderInventory orderInventory;
	private final GuestFolioService guestFolioService;
	private final ChargeRepository chargeRepository;
	private final GuestNotificationService guestNotificationService;

	public RoomServiceOrderServiceImpl(
			ProductRepository productRepository,
			BookingRepository bookingRepository,
			OrderRepository orderRepository,
			OrderItemRepository orderItemRepository,
			UserRepository userRepository,
			RoomServiceMapper roomServiceMapper,
			RoomServiceOrderInventory orderInventory,
			GuestFolioService guestFolioService,
			ChargeRepository chargeRepository,
			GuestNotificationService guestNotificationService
	) {
		this.productRepository = productRepository;
		this.bookingRepository = bookingRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.userRepository = userRepository;
		this.roomServiceMapper = roomServiceMapper;
		this.orderInventory = orderInventory;
		this.guestFolioService = guestFolioService;
		this.chargeRepository = chargeRepository;
		this.guestNotificationService = guestNotificationService;
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
		// El bloqueo evita crear un pedido mientras otra operación cambia el
		// estado de la reserva (por ejemplo, un checkout simultáneo).
		Booking booking = bookingRepository.findByIdForUpdate(request.bookingId())
				.orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + request.bookingId()));
		if (booking.getStatus() != BookingStatus.checked_in) {
			throw new BadRequestException(
					"Room service orders require a checked_in booking; current status: " + booking.getStatus());
		}
		Map<UUID, Integer> quantitiesByProductId = consolidateQuantities(request.items());

		Order order = roomServiceMapper.toOrderEntity(request, booking);
		OffsetDateTime now = OffsetDateTime.now();
		order.setRequestedAt(now);
		order.setCreatedAt(now);
		order.setUpdatedAt(now);
		order = orderRepository.save(order);

		Order persistedOrder = order;
		List<OrderItem> items = quantitiesByProductId.entrySet().stream()
				.map(entry -> toOrderItem(persistedOrder, entry.getKey(), entry.getValue()))
				.toList();
		items = orderItemRepository.saveAll(items);

		return roomServiceMapper.toOrderResponse(order, items);
	}

	@Override
	@Transactional
	public RoomServiceOrderResponse updateStatus(UUID orderId, OrderStatus status, String actorEmail) {
		// El bloqueo del pedido serializa sus cambios de estado: un mismo pedido
		// no puede descontar ni devolver inventario dos veces en paralelo.
		Order order = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new ResourceNotFoundException("Room service order not found: " + orderId));
		validateTransition(order.getStatus(), status);
		List<OrderItem> items = orderItemRepository.findByOrderIdOrderById(orderId);
		User actor = findActor(actorEmail);

		if (status == OrderStatus.accepted) {
			orderInventory.deduct(order, items, actor);
		} else if (status == OrderStatus.cancelled) {
			orderInventory.restore(order, actor);
		} else if (status == OrderStatus.delivered) {
			chargeToFolio(order, items, actorEmail);
		}

		order.setStatus(status);
		order.setUpdatedAt(OffsetDateTime.now());
		order = orderRepository.save(order);
		guestNotificationService.createIfAbsent(
				order.getBooking(),
				"room_service_" + status,
				"Room Service",
				"Your room service order is now " + status,
				"room_service_order",
				order.getId()
		);

		return roomServiceMapper.toOrderResponse(order, items);
	}

	/**
	 * Registra un único cargo por el total real del pedido en el folio abierto
	 * de la reserva, reutilizando las reglas del Folio (404 sin folio, 400 si
	 * no está abierto). Corre en la misma transacción que la entrega: si falla,
	 * el pedido sigue on_the_way y no queda ningún cargo.
	 */
	private void chargeToFolio(Order order, List<OrderItem> items, String actorEmail) {
		if (order.getCharge() != null) {
			return;
		}
		long totalCents = calculateTotalCents(items);
		if (totalCents <= 0) {
			throw new BadRequestException("Room service order total must be greater than zero to be delivered");
		}

		CreateChargeRequest chargeRequest = new CreateChargeRequest(
				"Room service order " + order.getId(),
				1,
				totalCents,
				ChargeCategory.consumption,
				null
		);
		ChargeResponse charge = guestFolioService.createCharge(order.getBooking().getId(), chargeRequest, actorEmail);
		order.setCharge(chargeRepository.getReferenceById(charge.id()));
	}

	private static long calculateTotalCents(List<OrderItem> items) {
		try {
			long total = 0;
			for (OrderItem item : items) {
				total = Math.addExact(total, Math.multiplyExact(item.getQuantity().longValue(), item.getUnitPriceCents()));
			}
			return total;
		} catch (ArithmeticException exception) {
			throw new BadRequestException("Room service order total is too large");
		}
	}

	private User findActor(String actorEmail) {
		if (actorEmail == null) {
			return null;
		}
		return userRepository.findByEmail(actorEmail).orElse(null);
	}

	/**
	 * Un producto repetido en el body se convierte en una sola línea con la
	 * suma de cantidades, conservando el orden de primera aparición.
	 */
	private static Map<UUID, Integer> consolidateQuantities(List<CreateRoomServiceOrderItemRequest> items) {
		Map<UUID, Integer> quantitiesByProductId = new LinkedHashMap<>();
		for (CreateRoomServiceOrderItemRequest item : items) {
			quantitiesByProductId.merge(item.productId(), item.quantity(), RoomServiceOrderServiceImpl::addQuantities);
		}
		return quantitiesByProductId;
	}

	private static int addQuantities(int current, int added) {
		try {
			return Math.addExact(current, added);
		} catch (ArithmeticException exception) {
			throw new BadRequestException("Room service item quantity is too large");
		}
	}

	private OrderItem toOrderItem(Order order, UUID productId, int quantity) {
		Product product = productRepository.findByIdAndActiveTrue(productId)
				.orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
		return roomServiceMapper.toOrderItemEntity(order, product, quantity);
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
			// Ya en camino, el pedido solo puede entregarse.
			case on_the_way -> next == OrderStatus.delivered;
			case delivered, rejected, cancelled -> false;
		};
	}
}
