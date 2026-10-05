package com.aurora.pms.service.impl;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.enums.BookingStatus;
import com.aurora.pms.model.enums.RoomStatus;
import com.aurora.pms.repository.BookingRepository;
import com.aurora.pms.repository.RateRepository;
import com.aurora.pms.repository.RoomRepository;

/**
 * Disponibilidad por tipo de habitación para la web pública.
 *
 * Disponibles = habitaciones operables del tipo − la mayor cantidad de reservas
 * activas del tipo que ocupan una misma noche de la estadía. Las reservas cuentan
 * tengan o no habitación asignada. Se calcula noche por noche para que dos
 * reservas que no se cruzan entre sí no consuman dos habitaciones.
 */
@Component
public class RoomTypeAvailability {

	/** Los mismos estados que bloquean una habitación en BookingServiceImpl. */
	static final Set<BookingStatus> BLOCKING_STATUSES = Set.of(
			BookingStatus.pending,
			BookingStatus.confirmed,
			BookingStatus.checked_in
	);

	/** Una habitación en estos estados no se puede vender. */
	static final Set<RoomStatus> NON_OPERABLE_ROOM_STATUSES = Set.of(
			RoomStatus.maintenance,
			RoomStatus.out_of_service
	);

	private final RoomRepository roomRepository;
	private final BookingRepository bookingRepository;
	private final RateRepository rateRepository;

	public RoomTypeAvailability(
			RoomRepository roomRepository,
			BookingRepository bookingRepository,
			RateRepository rateRepository
	) {
		this.roomRepository = roomRepository;
		this.bookingRepository = bookingRepository;
		this.rateRepository = rateRepository;
	}

	/**
	 * Habitaciones libres de cada tipo para toda la estadía [checkIn, checkOut).
	 * Los tipos sin habitaciones operables aparecen con 0.
	 */
	public Map<UUID, Integer> availableRooms(Collection<UUID> roomTypeIds, LocalDate checkIn, LocalDate checkOut) {
		Map<UUID, Integer> available = new HashMap<>();
		if (roomTypeIds.isEmpty()) {
			return available;
		}

		Map<UUID, Long> operableRooms = roomRepository
				.findByRoomTypeIdInAndStatusNotIn(roomTypeIds, NON_OPERABLE_ROOM_STATUSES).stream()
				.collect(Collectors.groupingBy(room -> room.getRoomType().getId(), Collectors.counting()));
		Map<UUID, List<Booking>> bookingsByRoomType = bookingRepository
				.findActiveOverlappingByRoomTypes(roomTypeIds, BLOCKING_STATUSES, checkIn, checkOut).stream()
				.collect(Collectors.groupingBy(booking -> booking.getRoomType().getId()));

		for (UUID roomTypeId : roomTypeIds) {
			long rooms = operableRooms.getOrDefault(roomTypeId, 0L);
			int busiestNight = busiestNight(bookingsByRoomType.getOrDefault(roomTypeId, List.of()), checkIn, checkOut);
			available.put(roomTypeId, (int) Math.max(0L, rooms - busiestNight));
		}
		return available;
	}

	/**
	 * Tarifa activa de cada tipo que cubre todas las noches de la estadía. Las
	 * tarifas de un mismo tipo no pueden solaparse, así que hay como máximo una;
	 * si aun así llegaran varias, gana la de inicio más reciente. No valida
	 * minimumNights: eso lo decide quien la usa.
	 */
	public Map<UUID, Rate> ratesCoveringStay(Collection<UUID> roomTypeIds, LocalDate checkIn, LocalDate checkOut) {
		Map<UUID, Rate> rates = new HashMap<>();
		if (roomTypeIds.isEmpty()) {
			return rates;
		}
		for (Rate rate : rateRepository.findActiveCoveringStay(roomTypeIds, checkIn, checkOut.minusDays(1))) {
			rates.putIfAbsent(rate.getRoomType().getId(), rate);
		}
		return rates;
	}

	private static int busiestNight(List<Booking> bookings, LocalDate checkIn, LocalDate checkOut) {
		int busiest = 0;
		for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
			LocalDate current = night;
			int occupied = (int) bookings.stream()
					.filter(booking -> !booking.getCheckIn().isAfter(current) && booking.getCheckOut().isAfter(current))
					.count();
			busiest = Math.max(busiest, occupied);
		}
		return busiest;
	}
}
