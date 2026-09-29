package com.aurora.pms.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.CreateChargeRequest;
import com.aurora.pms.dto.response.ChargeResponse;
import com.aurora.pms.dto.response.GuestFolioResponse;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.Charge;
import com.aurora.pms.model.GuestAccount;
import com.aurora.pms.model.Product;
import com.aurora.pms.model.enums.ChargeStatus;

@Component
public class GuestFolioMapper {

	public GuestFolioResponse toFolioResponse(GuestAccount account, List<Charge> charges, long completedPaymentsCents) {
		long activeChargesCents = 0;
		long voidedChargesCents = 0;
		for (Charge charge : charges) {
			if (charge.getStatus() == ChargeStatus.voided) {
				voidedChargesCents += charge.getAmountCents();
			} else {
				activeChargesCents += charge.getAmountCents();
			}
		}

		return new GuestFolioResponse(
				account.getId(),
				account.getBooking().getId(),
				account.getGuest().getId(),
				account.getStatus(),
				account.getBalanceCents(),
				account.getCurrency(),
				account.getOpenedAt(),
				account.getClosedAt(),
				activeChargesCents,
				voidedChargesCents,
				completedPaymentsCents,
				charges.stream().map(this::toChargeResponse).toList()
		);
	}

	public ChargeResponse toChargeResponse(Charge charge) {
		return new ChargeResponse(
				charge.getId(),
				charge.getBooking().getId(),
				charge.getProduct() != null ? charge.getProduct().getId() : null,
				charge.getDescription(),
				charge.getQuantity(),
				charge.getUnitPriceCents(),
				charge.getAmountCents(),
				charge.getCurrency(),
				charge.getCategory(),
				charge.getStatus(),
				charge.getChargedAt(),
				charge.getCreatedByUser() != null ? charge.getCreatedByUser().getId() : null,
				charge.getVoidReason(),
				charge.getCreatedAt()
		);
	}

	public Charge toChargeEntity(CreateChargeRequest request, Booking booking, Product product) {
		Charge charge = new Charge();
		charge.setBooking(booking);
		charge.setProduct(product);
		charge.setDescription(request.description().trim());
		charge.setQuantity(request.quantity());
		charge.setUnitPriceCents(request.unitPriceCents());
		charge.setCategory(request.category());
		return charge;
	}
}
