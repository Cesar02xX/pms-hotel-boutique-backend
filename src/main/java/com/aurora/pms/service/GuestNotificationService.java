package com.aurora.pms.service;

import java.util.List;
import java.util.UUID;

import com.aurora.pms.dto.response.GuestNotificationResponse;
import com.aurora.pms.model.Booking;

public interface GuestNotificationService {

	void createIfAbsent(Booking booking, String type, String title, String message, String resourceType, UUID resourceId);

	List<GuestNotificationResponse> findOwn(UUID bookingId);

	long countUnread(UUID bookingId);

	GuestNotificationResponse markRead(UUID bookingId, UUID notificationId);
}
