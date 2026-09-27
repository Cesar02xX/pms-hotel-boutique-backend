package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreateGuestRequest;
import com.aurora.pms.dto.request.UpdateGuestRequest;
import com.aurora.pms.dto.response.GuestResponse;

public interface GuestService {

	List<GuestResponse> findAll();

	GuestResponse findById(UUID id);

	GuestResponse create(CreateGuestRequest request);

	GuestResponse update(UUID id, UpdateGuestRequest request);
}
