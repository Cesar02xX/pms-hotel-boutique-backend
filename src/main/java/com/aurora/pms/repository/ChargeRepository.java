package com.aurora.pms.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.Charge;
import com.aurora.pms.model.enums.ChargeStatus;

public interface ChargeRepository extends JpaRepository<Charge, UUID> {

	List<Charge> findByBookingIdOrderByChargedAtAscCreatedAtAsc(UUID bookingId);

	Optional<Charge> findByIdAndBookingId(UUID id, UUID bookingId);

	@Query("""
			select coalesce(sum(c.amountCents), 0)
			from Charge c
			where c.booking.id = :bookingId
			  and c.status <> :excludedStatus
			""")
	long sumAmountCentsByBookingIdExcludingStatus(
			@Param("bookingId") UUID bookingId,
			@Param("excludedStatus") ChargeStatus excludedStatus
	);
}
