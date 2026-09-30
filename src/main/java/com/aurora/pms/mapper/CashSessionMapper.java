package com.aurora.pms.mapper;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aurora.pms.dto.request.CreateCashMovementRequest;
import com.aurora.pms.dto.response.CashMovementResponse;
import com.aurora.pms.dto.response.CashSessionResponse;
import com.aurora.pms.model.CashMovement;
import com.aurora.pms.model.CashSession;
import com.aurora.pms.model.User;

@Component
public class CashSessionMapper {

	public CashSessionResponse toResponse(
			CashSession session,
			long totalIncomeCents,
			long totalExpenseCents,
			long expectedBalanceCents
	) {
		return new CashSessionResponse(
				session.getId(),
				userId(session.getOpenedByUser()),
				session.getOpenedAt(),
				session.getOpeningBalanceCents(),
				session.getCurrency(),
				session.getStatus(),
				totalIncomeCents,
				totalExpenseCents,
				expectedBalanceCents,
				userId(session.getClosedByUser()),
				session.getClosedAt(),
				session.getCountedBalanceCents(),
				session.getDifferenceCents(),
				session.getNotes(),
				session.getCreatedAt(),
				session.getUpdatedAt()
		);
	}

	public CashMovementResponse toResponse(CashMovement movement) {
		return new CashMovementResponse(
				movement.getId(),
				movement.getCashSession().getId(),
				movement.getType(),
				movement.getConcept(),
				movement.getAmountCents(),
				movement.getCurrency(),
				userId(movement.getResponsibleUser()),
				movement.getOccurredAt(),
				movement.getPayment() != null ? movement.getPayment().getId() : null,
				movement.getCreatedAt()
		);
	}

	public CashMovement toEntity(CreateCashMovementRequest request, CashSession session) {
		CashMovement movement = new CashMovement();
		movement.setCashSession(session);
		movement.setType(request.type());
		movement.setConcept(request.concept().trim());
		movement.setAmountCents(request.amountCents());
		return movement;
	}

	private static UUID userId(User user) {
		return user != null ? user.getId() : null;
	}
}
