package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreateDepositRequest;
import com.aurora.pms.dto.request.RefundDepositRequest;
import com.aurora.pms.dto.response.DepositResponse;

public interface DepositService {

	List<DepositResponse> findAll();

	List<DepositResponse> findAllByBookingId(UUID bookingId);

	DepositResponse create(UUID bookingId, CreateDepositRequest request);

	DepositResponse refund(UUID bookingId, UUID depositId, RefundDepositRequest request);

	DepositResponse apply(UUID bookingId, UUID depositId);
}
