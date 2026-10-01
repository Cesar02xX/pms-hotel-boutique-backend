package com.aurora.pms.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.enums.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

	boolean existsByConfirmationCode(String confirmationCode);

	boolean existsByGuestLinkCode(String guestLinkCode);

	Optional<Booking> findByGuestLinkCode(String guestLinkCode);

	@Query("""
			select b
			from Booking b
			where b.checkIn <= :to
			  and b.checkOut >= :from
			""")
	java.util.List<Booking> findOverlappingDates(
			@Param("from") LocalDate from,
			@Param("to") LocalDate to
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from Booking b where b.id = :id")
	Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
			select count(b) > 0
			from Booking b
			where b.room.id = :roomId
			  and b.status in :statuses
			  and b.checkIn < :checkOut
			  and b.checkOut > :checkIn
			""")
	boolean existsActiveOverlap(
			@Param("roomId") UUID roomId,
			@Param("statuses") Collection<BookingStatus> statuses,
			@Param("checkIn") LocalDate checkIn,
			@Param("checkOut") LocalDate checkOut
	);

	@Query("""
			select count(b) > 0
			from Booking b
			where b.id <> :bookingId
			  and b.room.id = :roomId
			  and b.status in :statuses
			  and b.checkIn < :checkOut
			  and b.checkOut > :checkIn
			""")
	boolean existsActiveOverlapExcludingBooking(
			@Param("bookingId") UUID bookingId,
			@Param("roomId") UUID roomId,
			@Param("statuses") Collection<BookingStatus> statuses,
			@Param("checkIn") LocalDate checkIn,
			@Param("checkOut") LocalDate checkOut
	);

	@Query("""
			select count(b) > 0
			from Booking b
			where b.roomType.id = :roomTypeId
			  and b.status in :statuses
			  and b.checkOut > :fromDate
			  and (b.adults + b.children) > :capacity
			""")
	boolean existsActiveOrFutureOverCapacity(
			@Param("roomTypeId") UUID roomTypeId,
			@Param("statuses") Collection<BookingStatus> statuses,
			@Param("fromDate") LocalDate fromDate,
			@Param("capacity") int capacity
	);
}
