package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreateServiceRequestRequest;
import com.aurora.pms.dto.request.UpdateServiceRequestStatusRequest;
import com.aurora.pms.dto.response.ServiceRequestResponse;
import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;

public interface ServiceRequestService {

	List<ServiceRequestResponse> findAll(ServiceRequestType type, UUID bookingId, UUID roomId,
			ServiceRequestStatus status);

	ServiceRequestResponse findById(UUID id);

	ServiceRequestResponse create(CreateServiceRequestRequest request);

	ServiceRequestResponse updateStatus(UUID id, UpdateServiceRequestStatusRequest request, String actorEmail);
}
