package com.aurora.pms.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aurora.pms.model.CashSession;
import com.aurora.pms.model.enums.CashSessionStatus;

public interface CashSessionRepository extends JpaRepository<CashSession, UUID> {

	Optional<CashSession> findFirstByStatusOrderByOpenedAtDesc(CashSessionStatus status);

	boolean existsByStatus(CashSessionStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from CashSession s where s.id = :id")
	Optional<CashSession> findByIdForUpdate(@Param("id") UUID id);

	/**
	 * Serializa las aperturas de caja hasta el fin de la transacción: sin una
	 * fila que bloquear, dos aperturas simultáneas podrían crear dos sesiones.
	 */
	@Query(value = "select 1 from pg_advisory_xact_lock(hashtext('cash_sessions_open'))", nativeQuery = true)
	Integer lockOpening();
}
