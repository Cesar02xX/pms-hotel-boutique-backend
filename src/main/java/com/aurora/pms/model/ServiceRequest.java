package com.aurora.pms.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.ServiceRequestStatus;
import com.aurora.pms.model.enums.ServiceRequestType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "service_requests")
@Getter
@Setter
@NoArgsConstructor
public class ServiceRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "booking_id")
	private Booking booking;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "room_id")
	private Room room;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "guest_id")
	private Guest guest;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ServiceRequestType type;

	@Column(nullable = false, columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ServiceRequestStatus status;

	@Column(columnDefinition = "text")
	private String notes;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "charge_id")
	private Charge charge;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "responsible_user_id")
	private User responsibleUser;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "started_by_user_id")
	private User startedByUser;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "completed_by_user_id")
	private User completedByUser;

	@Column(name = "requested_at", nullable = false)
	private OffsetDateTime requestedAt;

	@Column(name = "started_at")
	private OffsetDateTime startedAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
