package com.aurora.pms.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.aurora.pms.dto.response.AuditLogResponse;
import com.aurora.pms.dto.response.StayReceiptResponse;

public interface ReportingService {

	StayReceiptResponse stayReceipt(UUID bookingId);

	Map<String, Object> operationalReport(LocalDate from, LocalDate to);

	List<AuditLogResponse> auditLogs(OffsetDateTime from, OffsetDateTime to);
}
