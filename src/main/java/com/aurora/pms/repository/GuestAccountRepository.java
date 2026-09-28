package com.aurora.pms.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.GuestAccount;

import jakarta.persistence.LockModeType;

public interface GuestAccountRepository extends JpaRepository<GuestAccount, UUID> {

	Optional<GuestAccount> findByBookingId(UUID bookingId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from GuestAccount a where a.booking.id = :bookingId")
	Optional<GuestAccount> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);

	/**
	 * Abre la cuenta solo si la reserva aún no tiene una. Devuelve 1 si la
	 * creó y 0 si ya existía, sin fallar ante aperturas concurrentes.
	 */
	@Modifying
	@Query(value = """
			insert into guest_accounts
			    (booking_id, guest_id, status, balance_cents, currency, opened_at, created_at, updated_at)
			values
			    (:bookingId, :guestId, 'open', :balanceCents, 'GTQ', :openedAt, :openedAt, :openedAt)
			on conflict (booking_id) do nothing
			""", nativeQuery = true)
	int insertOpenAccountIfAbsent(
			@Param("bookingId") UUID bookingId,
			@Param("guestId") UUID guestId,
			@Param("balanceCents") long balanceCents,
			@Param("openedAt") OffsetDateTime openedAt
	);
}
