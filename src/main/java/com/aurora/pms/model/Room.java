package com.aurora.pms.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.RoomHousekeepingStatus;
import com.aurora.pms.model.enums.RoomStatus;

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
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
public class Room {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "room_number", nullable = false, unique = true)
	private String roomNumber;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_type_id", nullable = false)
	private RoomType roomType;

	private Integer floor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RoomStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "housekeeping_status", nullable = false)
	private RoomHousekeepingStatus housekeepingStatus;

	@Column(columnDefinition = "text")
	private String notes;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cleaning_user_id")
	private User cleaningUser;

	@Column(name = "cleaning_started_at")
	private OffsetDateTime cleaningStartedAt;

	@Column(name = "cleaning_completed_at")
	private OffsetDateTime cleaningCompletedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cleaning_completed_by_user_id")
	private User cleaningCompletedByUser;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "inspector_user_id")
	private User inspectorUser;

	@Column(name = "inspected_at")
	private OffsetDateTime inspectedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
