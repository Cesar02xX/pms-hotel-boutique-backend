package com.aurora.pms.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.CashMovement;
import com.aurora.pms.model.enums.CashMovementType;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

	List<CashMovement> findByCashSessionIdOrderByOccurredAtAscCreatedAtAsc(UUID cashSessionId);

	@Query("select coalesce(sum(m.amountCents), 0) from CashMovement m "
			+ "where m.cashSession.id = :cashSessionId and m.type = :type")
	long sumAmountCents(@Param("cashSessionId") UUID cashSessionId, @Param("type") CashMovementType type);
}
