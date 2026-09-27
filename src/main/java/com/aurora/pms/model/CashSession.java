package com.aurora.pms.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.aurora.pms.model.enums.CashSessionStatus;

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
@Table(name = "cash_sessions")
@Getter
@Setter
@NoArgsConstructor
public class CashSession {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "opened_by_user_id", nullable = false)
	private User openedByUser;

	@Column(name = "opened_at", nullable = false)
	private OffsetDateTime openedAt;

	@Column(name = "opening_balance_cents", nullable = false)
	private Long openingBalanceCents;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, columnDefinition = "char(3)")
	private String currency = "GTQ";

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CashSessionStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "closed_by_user_id")
	private User closedByUser;

	@Column(name = "closed_at")
	private OffsetDateTime closedAt;

	@Column(name = "expected_balance_cents")
	private Long expectedBalanceCents;

	@Column(name = "counted_balance_cents")
	private Long countedBalanceCents;

	@Column(name = "difference_cents")
	private Long differenceCents;

	@Column(columnDefinition = "text")
	private String notes;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
