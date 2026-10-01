package com.aurora.pms.repository;

import java.util.List;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.Payment;
import com.aurora.pms.model.enums.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

	List<Payment> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);

	@Query("""
			select coalesce(sum(p.amountCents), 0)
			from Payment p
			where p.booking.id = :bookingId
			  and p.status = :status
			""")
	long sumAmountCentsByBookingIdAndStatus(
			@Param("bookingId") UUID bookingId,
			@Param("status") PaymentStatus status
	);

	@Query("""
			select coalesce(sum(p.amountCents), 0)
			from Payment p
			where p.status = :status
			  and p.paidAt >= :from
			  and p.paidAt < :to
			""")
	long sumAmountCentsByStatusAndPaidAtRange(
			@Param("status") PaymentStatus status,
			@Param("from") OffsetDateTime from,
			@Param("to") OffsetDateTime to
	);
}
