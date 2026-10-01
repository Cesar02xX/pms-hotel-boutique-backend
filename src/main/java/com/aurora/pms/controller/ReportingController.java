package com.aurora.pms.controller;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.response.AuditLogResponse;
import com.aurora.pms.dto.response.StayReceiptResponse;
import com.aurora.pms.service.ReportingService;

@RestController
@RequestMapping("/api/v1/admin")
public class ReportingController {

	private final ReportingService reportingService;

	public ReportingController(ReportingService reportingService) {
		this.reportingService = reportingService;
	}

	@GetMapping("/bookings/{bookingId}/receipt")
	public ResponseEntity<StayReceiptResponse> stayReceipt(@PathVariable UUID bookingId) {
		return ResponseEntity.ok(reportingService.stayReceipt(bookingId));
	}

	@GetMapping("/reports/operations")
	public ResponseEntity<Map<String, Object>> operations(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		return ResponseEntity.ok(reportingService.operationalReport(from, to));
	}

	@GetMapping("/audit-logs")
	public ResponseEntity<List<AuditLogResponse>> auditLogs(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
	) {
		return ResponseEntity.ok(reportingService.auditLogs(from, to));
	}
}
