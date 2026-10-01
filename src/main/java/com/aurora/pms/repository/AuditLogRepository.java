package com.aurora.pms.repository;

import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aurora.pms.model.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

	List<AuditLog> findByOccurredAtBetweenOrderByOccurredAtDesc(OffsetDateTime from, OffsetDateTime to);
}
