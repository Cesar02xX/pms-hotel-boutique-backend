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
@Table(name = "housekeeping_checklist_items")
@Getter
@Setter
@NoArgsConstructor
public class HousekeepingChecklistItem {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "checklist_id", nullable = false)
	private HousekeepingChecklist checklist;

	@Column(nullable = false, columnDefinition = "text")
	private String label;

	@Column(nullable = false)
	private boolean checked;

	@Column(nullable = false)
	private int position;

	@Column(columnDefinition = "text")
	private String notes;

	@Column(name = "checked_at")
	private OffsetDateTime checkedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "checked_by_user_id")
	private User checkedByUser;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
