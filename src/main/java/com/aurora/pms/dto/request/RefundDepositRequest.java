package com.aurora.pms.dto.request;

/**
 * El modelo no tiene columna de motivo de reembolso: si viene, se agrega a
 * las notas del depósito.
 */
public record RefundDepositRequest(
		String reason
) {
}
