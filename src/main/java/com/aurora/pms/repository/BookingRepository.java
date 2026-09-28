package com.aurora.pms.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.enums.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

	boolean existsByConfirmationCode(String confirmationCode);

	boolean existsByGuestLinkCode(String guestLinkCode);

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
}
