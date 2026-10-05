package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreateConciergeRequestRequest;
import com.aurora.pms.dto.request.UpdateConciergeRequestRequest;
import com.aurora.pms.dto.request.UpdateConciergeRequestStatusRequest;
import com.aurora.pms.dto.response.ConciergeRequestResponse;
import com.aurora.pms.model.enums.ServiceRequestStatus;

public interface ConciergeRequestService {

	List<ConciergeRequestResponse> findAll(UUID bookingId, ServiceRequestStatus status);

	ConciergeRequestResponse findById(UUID requestId);

	ConciergeRequestResponse create(CreateConciergeRequestRequest request);

	/** Sin actor autenticado (p. ej. cancelacion desde el portal del huesped): no asigna responsable. */
	ConciergeRequestResponse updateStatus(UUID requestId, UpdateConciergeRequestStatusRequest request);

	/**
	 * Si no llega {@code responsibleUserId} y la solicitud aun no tiene responsable, al aceptarla,
	 * iniciarla o completarla se asigna el usuario autenticado ({@code actorEmail}).
	 */
	ConciergeRequestResponse updateStatus(
			UUID requestId,
			UpdateConciergeRequestStatusRequest request,
			String actorEmail
	);

	ConciergeRequestResponse update(UUID requestId, UpdateConciergeRequestRequest request);
}
