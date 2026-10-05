package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CreatePaymentRequest;
import com.aurora.pms.dto.response.PaymentResponse;

public interface PaymentService {

	List<PaymentResponse> findAll();

	List<PaymentResponse> findAllByBookingId(UUID bookingId);

	PaymentResponse create(UUID bookingId, CreatePaymentRequest request, String actorEmail);
}
