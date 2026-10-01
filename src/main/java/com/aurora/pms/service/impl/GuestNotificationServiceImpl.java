package com.aurora.pms.service.impl;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aurora.pms.dto.response.GuestNotificationResponse;
import com.aurora.pms.exception.ResourceNotFoundException;
import com.aurora.pms.model.Booking;
import com.aurora.pms.model.GuestNotification;
import com.aurora.pms.repository.GuestNotificationRepository;
import com.aurora.pms.service.GuestNotificationService;

@Service
public class GuestNotificationServiceImpl implements GuestNotificationService {

	private final GuestNotificationRepository notificationRepository;

	public GuestNotificationServiceImpl(GuestNotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	@Override
	@Transactional
	public void createIfAbsent(
			Booking booking,
			String type,
			String title,
			String message,
			String resourceType,
			UUID resourceId
	) {
		if (notificationRepository.existsByBookingIdAndResourceTypeAndResourceIdAndType(
				booking.getId(), resourceType, resourceId, type)) {
			return;
		}
		GuestNotification notification = new GuestNotification();
		notification.setBooking(booking);
		notification.setGuest(booking.getGuest());
		notification.setType(type);
		notification.setTitle(title);
		notification.setMessage(message);
		notification.setResourceType(resourceType);
		notification.setResourceId(resourceId);
		notification.setCreatedAt(OffsetDateTime.now());
		notificationRepository.save(notification);
	}

	@Override
	@Transactional(readOnly = true)
	public List<GuestNotificationResponse> findOwn(UUID bookingId) {
		return notificationRepository.findByBookingIdOrderByCreatedAtDesc(bookingId).stream()
				.map(this::toResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public long countUnread(UUID bookingId) {
		return notificationRepository.countByBookingIdAndReadAtIsNull(bookingId);
	}

	@Override
	@Transactional
	public GuestNotificationResponse markRead(UUID bookingId, UUID notificationId) {
		GuestNotification notification = notificationRepository.findByIdAndBookingId(notificationId, bookingId)
				.orElseThrow(() -> new ResourceNotFoundException("Guest notification not found: " + notificationId));
		if (notification.getReadAt() == null) {
			notification.setReadAt(OffsetDateTime.now());
		}
		return toResponse(notification);
	}

	private GuestNotificationResponse toResponse(GuestNotification notification) {
		return new GuestNotificationResponse(
				notification.getId(),
				notification.getType(),
				notification.getTitle(),
				notification.getMessage(),
				notification.getResourceType(),
				notification.getResourceId(),
				notification.getReadAt() != null,
				notification.getReadAt(),
				notification.getCreatedAt()
		);
	}
}
