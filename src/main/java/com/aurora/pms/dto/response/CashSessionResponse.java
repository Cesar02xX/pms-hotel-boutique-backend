package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.CashSessionStatus;

/**
 * Totales calculados en backend. expectedBalanceCents es el saldo esperado en
 * vivo mientras la sesión está abierta y el valor guardado al cerrarla.
 */
public record CashSessionResponse(
		UUID id,
		UUID openedByUserId,
		OffsetDateTime openedAt,
		Long openingBalanceCents,
		String currency,
		CashSessionStatus status,
		Long totalIncomeCents,
		Long totalExpenseCents,
		Long expectedBalanceCents,
		UUID closedByUserId,
		OffsetDateTime closedAt,
		Long countedBalanceCents,
		Long differenceCents,
		String notes,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt
) {
}
