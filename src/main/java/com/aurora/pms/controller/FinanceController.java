package com.aurora.pms.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.pms.dto.response.ApiErrorResponse;
import com.aurora.pms.dto.response.ChargeResponse;
import com.aurora.pms.dto.response.DepositResponse;
import com.aurora.pms.dto.response.GuestFolioResponse;
import com.aurora.pms.dto.response.PaymentResponse;
import com.aurora.pms.service.DepositService;
import com.aurora.pms.service.GuestFolioService;
import com.aurora.pms.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Finance", description = "Consultas financieras globales")
@ApiResponses({
		@ApiResponse(responseCode = "401", description = "Missing or invalid token",
				content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class FinanceController {

	private final GuestFolioService guestFolioService;
	private final PaymentService paymentService;
	private final DepositService depositService;

	public FinanceController(
			GuestFolioService guestFolioService,
			PaymentService paymentService,
			DepositService depositService
	) {
		this.guestFolioService = guestFolioService;
		this.paymentService = paymentService;
		this.depositService = depositService;
	}

	@GetMapping("/guest-accounts")
	@Operation(summary = "List all guest accounts")
	public ResponseEntity<List<GuestFolioResponse>> findGuestAccounts() {
		return ResponseEntity.ok(guestFolioService.findAllFolios());
	}

	@GetMapping("/charges")
	@Operation(summary = "List all charges")
	public ResponseEntity<List<ChargeResponse>> findCharges() {
		return ResponseEntity.ok(guestFolioService.findAllCharges());
	}

	@GetMapping("/payments")
	@Operation(summary = "List all payments")
	public ResponseEntity<List<PaymentResponse>> findPayments() {
		return ResponseEntity.ok(paymentService.findAll());
	}

	@GetMapping("/deposits")
	@Operation(summary = "List all deposits")
	public ResponseEntity<List<DepositResponse>> findDeposits() {
		return ResponseEntity.ok(depositService.findAll());
	}
}
