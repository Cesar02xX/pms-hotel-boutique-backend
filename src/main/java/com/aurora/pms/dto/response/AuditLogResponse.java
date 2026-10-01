package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogResponse(
		UUID id,
		UUID userId,
		String userEmail,
		String module,
		String action,
		String entityType,
		UUID entityId,
		OffsetDateTime occurredAt,
		String details
) {
}
