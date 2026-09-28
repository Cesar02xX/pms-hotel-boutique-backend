package com.aurora.pms.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.BookingCompanion;

public interface BookingCompanionRepository extends JpaRepository<BookingCompanion, UUID> {

	List<BookingCompanion> findByBookingIdOrderByCreatedAt(UUID bookingId);

	Optional<BookingCompanion> findByIdAndBookingId(UUID id, UUID bookingId);
}
