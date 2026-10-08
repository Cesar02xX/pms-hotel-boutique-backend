package com.aurora.pms.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "housekeeping_checklist_templates")
@Getter
@Setter
@NoArgsConstructor
public class HousekeepingChecklistTemplate {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 80)
	private String code;

	@Column(nullable = false, length = 120)
	private String name;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "housekeeping_checklist_template_items",
			joinColumns = @JoinColumn(name = "template_id"))
	@OrderColumn(name = "position")
	@Column(name = "label", nullable = false, columnDefinition = "text")
	private List<String> items = new ArrayList<>();

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
