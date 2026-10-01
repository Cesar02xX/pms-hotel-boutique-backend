package com.aurora.pms.security;

import java.util.UUID;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.repository.BookingRepository;

@Service
public class GuestPrincipalService {

	private final BookingRepository bookingRepository;

	public GuestPrincipalService(BookingRepository bookingRepository) {
		this.bookingRepository = bookingRepository;
	}

	@Transactional(readOnly = true)
	public GuestPrincipal loadByBookingId(UUID bookingId) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new UsernameNotFoundException("Guest booking not found"));
		if (booking.getStatus() != BookingStatus.checked_in) {
			throw new UsernameNotFoundException("Guest stay is not active");
		}
		return new GuestPrincipal(booking.getId(), booking.getGuest().getId(), booking.getGuestLinkCode());
	}
}
