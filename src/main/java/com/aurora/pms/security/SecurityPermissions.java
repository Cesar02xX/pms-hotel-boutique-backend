package com.aurora.pms.security;

import java.util.List;

public final class SecurityPermissions {

	public static final String ROOMS_READ = "rooms.read";
	public static final String ROOMS_WRITE = "rooms.write";
	public static final String ROOM_TYPES_READ = "room-types.read";
	public static final String ROOM_TYPES_WRITE = "room-types.write";
	public static final String ROOM_FEATURES_READ = "room-features.read";
	public static final String RATES_READ = "rates.read";
	public static final String RATES_WRITE = "rates.write";
	public static final String GUESTS_READ = "guests.read";
	public static final String GUESTS_WRITE = "guests.write";
	public static final String BOOKINGS_READ = "bookings.read";
	public static final String BOOKINGS_WRITE = "bookings.write";
	public static final String BOOKINGS_CHECK_IN = "bookings.check-in";
	public static final String BOOKINGS_CHECK_OUT = "bookings.check-out";
	public static final String BOOKING_COMPANIONS_READ = "booking-companions.read";
	public static final String BOOKING_COMPANIONS_WRITE = "booking-companions.write";
	public static final String HOUSEKEEPING_READ = "housekeeping.read";
	public static final String HOUSEKEEPING_WRITE = "housekeeping.write";
	public static final String ROOM_SERVICE_READ = "room-service.read";
	public static final String ROOM_SERVICE_WRITE = "room-service.write";
	public static final String PAYMENTS_READ = "payments.read";
	public static final String PAYMENTS_WRITE = "payments.write";
	public static final String DEPOSITS_READ = "deposits.read";
	public static final String DEPOSITS_WRITE = "deposits.write";
	public static final String FOLIOS_READ = "folios.read";
	public static final String FOLIOS_WRITE = "folios.write";
	public static final String CHARGES_READ = "charges.read";
	public static final String CHARGES_WRITE = "charges.write";
	public static final String INVENTORY_READ = "inventory.read";
	public static final String INVENTORY_WRITE = "inventory.write";
	public static final String CASH_READ = "cash.read";
	public static final String CASH_WRITE = "cash.write";
	public static final String CONCIERGE_READ = "concierge.read";
	public static final String CONCIERGE_WRITE = "concierge.write";
	public static final String SERVICE_REQUESTS_READ = "service-requests.read";
	public static final String SERVICE_REQUESTS_WRITE = "service-requests.write";

	public static final List<String> ALL = List.of(
			ROOMS_READ,
			ROOMS_WRITE,
			ROOM_TYPES_READ,
			ROOM_TYPES_WRITE,
			ROOM_FEATURES_READ,
			RATES_READ,
			RATES_WRITE,
			GUESTS_READ,
			GUESTS_WRITE,
			BOOKINGS_READ,
			BOOKINGS_WRITE,
			BOOKINGS_CHECK_IN,
			BOOKINGS_CHECK_OUT,
			BOOKING_COMPANIONS_READ,
			BOOKING_COMPANIONS_WRITE,
			HOUSEKEEPING_READ,
			HOUSEKEEPING_WRITE,
			ROOM_SERVICE_READ,
			ROOM_SERVICE_WRITE,
			PAYMENTS_READ,
			PAYMENTS_WRITE,
			DEPOSITS_READ,
			DEPOSITS_WRITE,
			FOLIOS_READ,
			FOLIOS_WRITE,
			CHARGES_READ,
			CHARGES_WRITE,
			INVENTORY_READ,
			INVENTORY_WRITE,
			CASH_READ,
			CASH_WRITE,
			CONCIERGE_READ,
			CONCIERGE_WRITE,
			SERVICE_REQUESTS_READ,
			SERVICE_REQUESTS_WRITE
	);

	private SecurityPermissions() {
	}
}
