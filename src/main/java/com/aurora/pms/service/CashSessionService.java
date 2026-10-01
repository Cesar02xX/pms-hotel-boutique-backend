package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.request.CloseCashSessionRequest;
import com.aurora.pms.dto.request.CreateCashMovementRequest;
import com.aurora.pms.dto.request.OpenCashSessionRequest;
import com.aurora.pms.dto.response.CashMovementResponse;
import com.aurora.pms.dto.response.CashSessionResponse;

public interface CashSessionService {

	CashSessionResponse findCurrent(String actorEmail);

	CashSessionResponse open(OpenCashSessionRequest request, String actorEmail);

	CashSessionResponse close(UUID sessionId, CloseCashSessionRequest request, String actorEmail);

	List<CashMovementResponse> findMovements(UUID sessionId);

	CashMovementResponse createMovement(UUID sessionId, CreateCashMovementRequest request, String actorEmail);
}
