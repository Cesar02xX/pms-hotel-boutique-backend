package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.SaveHousekeepingServiceRequest;
import com.aurora.pms.dto.response.HousekeepingServiceOptionResponse;

public interface HousekeepingCatalogService {
	List<HousekeepingServiceOptionResponse> findAll(boolean activeOnly);
	HousekeepingServiceOptionResponse create(SaveHousekeepingServiceRequest request);
	HousekeepingServiceOptionResponse update(UUID id, SaveHousekeepingServiceRequest request);
}
