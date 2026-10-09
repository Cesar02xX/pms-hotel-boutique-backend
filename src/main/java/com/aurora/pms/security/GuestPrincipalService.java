package com.aurora.pms.security;

import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.repository.BookingRepository;

@Service
public class GuestPrincipalService {
	private static final ZoneId HOTEL_ZONE = ZoneId.of("America/Guatemala");
	private static final EnumSet<BookingStatus> PORTAL_STATUSES = EnumSet.of(
			BookingStatus.pending, BookingStatus.confirmed, BookingStatus.checked_in);

	private final BookingRepository bookingRepository;

	public GuestPrincipalService(BookingRepository bookingRepository) {
		this.bookingRepository = bookingRepository;
	}

	@Transactional(readOnly = true)
	public GuestPrincipal loadByBookingId(UUID bookingId) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new UsernameNotFoundException("Guest booking not found"));
		if (!PORTAL_STATUSES.contains(booking.getStatus())
				|| !LocalDate.now(HOTEL_ZONE).isBefore(booking.getCheckOut())) {
			throw new UsernameNotFoundException("Guest reservation is not available");
		}
		return new GuestPrincipal(booking.getId(), booking.getGuest().getId(), booking.getGuestLinkCode());
	}
}
