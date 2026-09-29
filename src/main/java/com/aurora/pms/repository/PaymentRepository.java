package com.aurora.pms.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	List<Payment> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);
}
