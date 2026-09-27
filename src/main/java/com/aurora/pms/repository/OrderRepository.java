package com.aurora.pms.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {
}
