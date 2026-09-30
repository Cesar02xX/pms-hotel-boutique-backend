package com.aurora.pms.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

	List<OrderItem> findByOrderIdOrderById(UUID orderId);

	List<OrderItem> findByOrderIdIn(Collection<UUID> orderIds);
}
