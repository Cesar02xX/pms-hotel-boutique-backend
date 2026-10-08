package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.SaveConciergeServiceRequest;
import com.aurora.pms.dto.response.ConciergeServiceResponse;

public interface ConciergeCatalogService {
	List<ConciergeServiceResponse> findAll(boolean activeOnly);
	ConciergeServiceResponse create(SaveConciergeServiceRequest request);
	ConciergeServiceResponse update(UUID id, SaveConciergeServiceRequest request);
}
