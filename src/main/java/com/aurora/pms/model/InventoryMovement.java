package com.aurora.pms.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.aurora.pms.model.enums.InventoryMovementReason;
import com.aurora.pms.model.enums.InventoryMovementType;

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
@Table(name = "inventory_movements")
@Getter
@Setter
@NoArgsConstructor
public class InventoryMovement {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "inventory_item_id", nullable = false)
	private InventoryItem inventoryItem;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InventoryMovementType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InventoryMovementReason reason;

	@Column(nullable = false)
	private Integer quantity;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "responsible_user_id")
	private User responsibleUser;

	@Column(name = "occurred_at", nullable = false)
	private OffsetDateTime occurredAt;

	@Column(columnDefinition = "text")
	private String notes;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;
}
