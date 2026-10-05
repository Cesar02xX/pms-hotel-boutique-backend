package com.aurora.pms.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.aurora.pms.model.enums.HousekeepingChecklistStatus;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "housekeeping_checklists")
@Getter
@Setter
@NoArgsConstructor
public class HousekeepingChecklist {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "service_request_id", nullable = false)
	private ServiceRequest serviceRequest;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "room_id", nullable = false)
	private Room room;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "responsible_user_id")
	private User responsibleUser;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "completed_by_user_id")
	private User completedByUser;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private HousekeepingChecklistStatus status;

	@Column(columnDefinition = "text")
	private String observations;

	@Column(name = "started_at")
	private OffsetDateTime startedAt;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@OneToMany(mappedBy = "checklist", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC, createdAt ASC")
	private List<HousekeepingChecklistItem> items = new ArrayList<>();
}
