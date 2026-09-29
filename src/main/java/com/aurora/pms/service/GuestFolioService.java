package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreateChargeRequest;
import com.aurora.pms.dto.request.VoidChargeRequest;
import com.aurora.pms.dto.response.ChargeResponse;
import com.aurora.pms.dto.response.GuestFolioResponse;

public interface GuestFolioService {

	GuestFolioResponse getFolio(UUID bookingId);

	OpenFolioResult openFolio(UUID bookingId);

	List<ChargeResponse> findCharges(UUID bookingId);

	ChargeResponse createCharge(UUID bookingId, CreateChargeRequest request, String actorEmail);

	ChargeResponse voidCharge(UUID bookingId, UUID chargeId, VoidChargeRequest request);

	/** Folio resultante y si la llamada lo creó (false cuando ya existía). */
	record OpenFolioResult(GuestFolioResponse folio, boolean created) {
	}
}
