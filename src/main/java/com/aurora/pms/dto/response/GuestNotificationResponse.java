package com.aurora.pms.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GuestNotificationResponse(
		UUID id,
		String type,
		String title,
		String message,
		String resourceType,
		UUID resourceId,
		boolean read,
		OffsetDateTime readAt,
		OffsetDateTime createdAt
) {
}
