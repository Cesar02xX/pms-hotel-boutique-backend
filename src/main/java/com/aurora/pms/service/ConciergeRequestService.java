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

	ConciergeRequestResponse updateStatus(UUID requestId, UpdateConciergeRequestStatusRequest request);

	ConciergeRequestResponse update(UUID requestId, UpdateConciergeRequestRequest request);
}
