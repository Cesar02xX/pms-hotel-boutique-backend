package com.aurora.pms.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.PublicGuestRequest;
import com.aurora.pms.dto.response.BookingResponse;
import com.aurora.pms.dto.response.PublicAvailabilityResult;
import com.aurora.pms.dto.response.PublicBookingResponse;
import com.aurora.pms.dto.response.PublicRateResponse;
import com.aurora.pms.dto.response.PublicRoomFeatureResponse;
import com.aurora.pms.dto.response.PublicRoomTypeResponse;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.RoomFeature;
import com.aurora.pms.model.RoomType;

/**
 * Respuestas de la web pública: solo lo que la web necesita mostrar, sin
 * banderas internas, timestamps de auditoría ni identificadores de huésped.
 * Los datos del huésped de una reserva repiten lo que envió la petición, nunca
 * lo guardado en la base.
 */
@Component
public class PublicBookingMapper {

	public PublicRoomTypeResponse toRoomTypeResponse(RoomType roomType, List<RoomFeature> features) {
		return new PublicRoomTypeResponse(
				roomType.getId(),
				roomType.getCode(),
				roomType.getName(),
				roomType.getDescription(),
				roomType.getCapacity(),
				roomType.getBedConfiguration(),
				features.stream().map(this::toFeatureResponse).toList()
		);
	}

	public PublicRateResponse toRateResponse(Rate rate) {
		return new PublicRateResponse(
				rate.getId(),
				rate.getRoomType().getId(),
				rate.getName(),
				rate.getValidFrom(),
				rate.getValidTo(),
				rate.getPriceCents(),
				rate.getCurrency(),
				rate.getMinimumNights(),
				rate.getRefundable()
		);
	}

	public PublicAvailabilityResult toAvailabilityResult(RoomType roomType, int availableRooms, Rate rate, int nights) {
		return new PublicAvailabilityResult(
				roomType.getId(),
				roomType.getCode(),
				roomType.getName(),
				roomType.getCapacity(),
				roomType.getBedConfiguration(),
				availableRooms,
				toRateResponse(rate),
				rate.getPriceCents() * nights,
				rate.getCurrency()
		);
	}

	public PublicBookingResponse toBookingResponse(
			BookingResponse booking,
			RoomType roomType,
			Rate rate,
			PublicGuestRequest guest,
			int nights
	) {
		return new PublicBookingResponse(
				booking.confirmationCode(),
				booking.status(),
				roomType.getId(),
				roomType.getName(),
				booking.checkIn(),
				booking.checkOut(),
				nights,
				booking.adults(),
				booking.children(),
				rate.getName(),
				booking.totalAmountCents(),
				booking.currency(),
				guest.firstName().trim(),
				guest.lastName().trim(),
				guest.email().trim(),
				booking.createdAt()
		);
	}

	private PublicRoomFeatureResponse toFeatureResponse(RoomFeature feature) {
		return new PublicRoomFeatureResponse(feature.getId(), feature.getName(), feature.getDescription());
	}
}
