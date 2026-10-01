package com.aurora.pms.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "guest_notifications")
@Getter
@Setter
@NoArgsConstructor
public class GuestNotification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "guest_id", nullable = false)
	private Guest guest;

	@Column(nullable = false)
	private String type;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String message;

	@Column(name = "resource_type")
	private String resourceType;

	@Column(name = "resource_id")
	private UUID resourceId;

	@Column(name = "read_at")
	private OffsetDateTime readAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;
}
