package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreateRateRequest;
import com.aurora.pms.dto.request.UpdateRateRequest;
import com.aurora.pms.dto.response.RateResponse;

public interface RateService {

	List<RateResponse> findAll();

	RateResponse create(CreateRateRequest request);

	RateResponse update(UUID id, UpdateRateRequest request);
}
