package com.aurora.pms.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.GuestNotification;

public interface GuestNotificationRepository extends JpaRepository<GuestNotification, UUID> {

	List<GuestNotification> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

	long countByBookingIdAndReadAtIsNull(UUID bookingId);

	Optional<GuestNotification> findByIdAndBookingId(UUID id, UUID bookingId);

	boolean existsByBookingIdAndResourceTypeAndResourceIdAndType(
			UUID bookingId,
			String resourceType,
			UUID resourceId,
			String type
	);
}
