package com.aurora.pms.mapper;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.CreateRateRequest;
import com.aurora.pms.dto.request.UpdateRateRequest;
import com.aurora.pms.dto.response.RateResponse;
import com.aurora.pms.model.Rate;
import com.aurora.pms.model.RoomType;

@Component
public class RateMapper {

	public RateResponse toResponse(Rate rate) {
		return new RateResponse(
				rate.getId(),
				rate.getRoomType().getId(),
				rate.getName(),
				rate.getValidFrom(),
				rate.getValidTo(),
				rate.getPriceCents(),
				rate.getCurrency(),
				rate.getMinimumNights(),
				rate.getRefundable(),
				rate.getActive(),
				rate.getCreatedAt(),
				rate.getUpdatedAt()
		);
	}

	public Rate toEntity(CreateRateRequest request, RoomType roomType) {
		Rate rate = new Rate();
		rate.setRoomType(roomType);
		rate.setName(request.name().trim());
		rate.setValidFrom(request.validFrom());
		rate.setValidTo(request.validTo());
		rate.setPriceCents(request.priceCents());
		if (request.currency() != null) {
			rate.setCurrency(request.currency());
		}
		rate.setMinimumNights(request.minimumNights());
		if (request.refundable() != null) {
			rate.setRefundable(request.refundable());
		}
		if (request.active() != null) {
			rate.setActive(request.active());
		}
		return rate;
	}

	/**
	 * Aplica solo los campos presentes en el request. La relación con RoomType
	 * y la coherencia de fechas las valida el servicio.
	 */
	public void applyUpdate(Rate rate, UpdateRateRequest request) {
		if (request.name() != null) {
			rate.setName(request.name().trim());
		}
		if (request.validFrom() != null) {
			rate.setValidFrom(request.validFrom());
		}
		if (request.validTo() != null) {
			rate.setValidTo(request.validTo());
		}
		if (request.priceCents() != null) {
			rate.setPriceCents(request.priceCents());
		}
		if (request.currency() != null) {
			rate.setCurrency(request.currency());
		}
		if (request.minimumNights() != null) {
			rate.setMinimumNights(request.minimumNights());
		}
		if (request.refundable() != null) {
			rate.setRefundable(request.refundable());
		}
		if (request.active() != null) {
			rate.setActive(request.active());
		}
	}
}
