package com.aurora.pms.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.Deposit;

import jakarta.persistence.LockModeType;

public interface DepositRepository extends JpaRepository<Deposit, UUID> {

	List<Deposit> findByBookingIdOrderByCollectedAtAscCreatedAtAsc(UUID bookingId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from Deposit d where d.id = :id and d.booking.id = :bookingId")
	Optional<Deposit> findByIdAndBookingIdForUpdate(@Param("id") UUID id, @Param("bookingId") UUID bookingId);
}
